package com.github.xandergos.terraindiffusionmc.hydro;

import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Builds {@link HydrologyTile} entries from heightmap data.
 *
 * <p>Tiles are aligned on a {@link #TILE_SIZE}-block grid. Each tile is computed from a heightmap
 * extended by {@link #PADDING} cells on every side : pit-filling and D8 flow direction need the
 * neighboring terrain on either side of the tile boundary to behave correctly. The hydrological
 * arrays are then cropped back to the central tile region for storage.
 *
 * <p><b>Cross-tile stitching (cascade depth 1).</b> Standalone-tile accumulation underestimates
 * flow near the borders because upstream neighbors haven't contributed. To fix this when a
 * tile T is being built it looks up its 4 neighbors in the cache : if any are present read
 * their outgoing flux toward us and feed it as boundary inflow to our own accumulation pass. To
 * keep already-built tiles consistent in the other direction after T finishes it looks at the
 * flux T sends to each neighbor ; if a cached neighbor was built without that contribution
 * it invalidates and rebuild it. This rebuild does not cascade further (depth 1) to bound CPU.
 *
 * <p>Cached in an LRU map capped at {@code MAX_CACHE_SIZE} entries. Builds run on a dedicated
 * daemon thread so the render thread never blocks ; {@link #getOrSchedule} returns {@code null}
 * for tiles still in progress.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyBuilder {

    /** Block-space tile side length ; tiles are aligned on multiples of this value. */
    public static final int TILE_SIZE = 256;

    /** Cells of overlap fetched on every side before computation then cropped out. */
    public static final int PADDING = 16;

    /** Minimum accumulated flux on a boundary cell before a stitching rebuild is triggered. */
    private static final int STITCH_FLUX_THRESHOLD = 50;

    private static final int MAX_CACHE_SIZE = 16;
    private static final Object CACHE_LOCK = new Object();
    private static final Map<Long, HydrologyTile> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Long, Future<HydrologyTile>> PENDING = new ConcurrentHashMap<>();
    private static final ExecutorService BUILD_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "hydrology-builder");
                thread.setDaemon(true);
                return thread;
            });

    private HydrologyBuilder() {}

    /** Schedule mode : initial first-time build allowed to cascade to neighbors. */
    private static final int SCHEDULE_INITIAL = 0;
    /** Schedule mode : rebuild triggered by a neighbor ; NOT allowed to cascade further. */
    private static final int SCHEDULE_RESTITCH = 1;

    /**
     * Returns the cached tile at the given block-space origin or {@code null} if it is not yet
     * built. Schedules an async build when the tile is missing.
     */
    public static HydrologyTile getOrSchedule(int blockI, int blockJ) {
        long key = tileKey(Math.floorDiv(blockI, TILE_SIZE), Math.floorDiv(blockJ, TILE_SIZE));
        synchronized (CACHE_LOCK) {
            HydrologyTile cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        scheduleBuild(key, SCHEDULE_INITIAL);
        return null;
    }

    /**
     * Synchronous variant of {@link #getOrSchedule} : returns the cached tile if present
     * otherwise computes it on the calling thread and inserts it in the cache. Used by the
     * worldgen pipeline where it needs the data immediately and the chunk thread is allowed to
     * block ; the renderer should keep using {@link #getOrSchedule} so the render thread never
     * stalls.
     */
    public static HydrologyTile getOrCompute(int blockI, int blockJ) {
        int tileI = Math.floorDiv(blockI, TILE_SIZE);
        int tileJ = Math.floorDiv(blockJ, TILE_SIZE);
        long key = tileKey(tileI, tileJ);
        synchronized (CACHE_LOCK) {
            HydrologyTile cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        // Build inline. We don't go through the executor here because the caller wants the
        // result immediately.
        int originBlockI = tileI * TILE_SIZE;
        int originBlockJ = tileJ * TILE_SIZE;
        HeightmapData paddedData = LocalTerrainProvider.getInstance()
                .fetchHeightmap(originBlockI - PADDING, originBlockJ - PADDING,
                        originBlockI + TILE_SIZE + PADDING, originBlockJ + TILE_SIZE + PADDING);
        FlowAccumulation.InboundFlux inbound = collectInboundFluxes(tileI, tileJ);
        HydrologyTile tile = build(originBlockI, originBlockJ, tileI, tileJ, paddedData, inbound);
        synchronized (CACHE_LOCK) {
            CACHE.put(key, tile);
            evictLruTo(MAX_CACHE_SIZE);
        }
        // No cascade from the synchronous path : neighbors that are already cached and might be
        // out of date will be picked up when they're rebuilt by their own demand or via a
        // manual cache rebuild from the settings screen. Keeping the synchronous path short
        // avoids surprise cascades on the chunk worker thread.
        return tile;
    }

    /** Clears all cached and pending tiles. Call when the world or seed changes. */
    public static void clearCache() {
        synchronized (CACHE_LOCK) {
            CACHE.clear();
        }
        PENDING.clear();
    }

    private static void scheduleBuild(long key, int scheduleMode) {
        if (PENDING.containsKey(key)) return;

        int tileI = (int) (key >> 32);
        int tileJ = (int) key;
        int originBlockI = tileI * TILE_SIZE;
        int originBlockJ = tileJ * TILE_SIZE;

        FutureTask<HydrologyTile> task = new FutureTask<>(() -> {
            HeightmapData paddedData = LocalTerrainProvider.getInstance()
                    .fetchHeightmap(originBlockI - PADDING, originBlockJ - PADDING,
                            originBlockI + TILE_SIZE + PADDING, originBlockJ + TILE_SIZE + PADDING);

            // Read inbound flux from any cached neighbors so our accumulation starts from
            // the correct boundary conditions.
            FlowAccumulation.InboundFlux inbound = collectInboundFluxes(tileI, tileJ);

            HydrologyTile tile = build(originBlockI, originBlockJ, tileI, tileJ, paddedData, inbound);
            synchronized (CACHE_LOCK) {
                CACHE.put(key, tile);
                evictLruTo(MAX_CACHE_SIZE);
            }
            PENDING.remove(key);

            // Cascade : if this is a first-time build ; propagate our outgoing flux to neighbors
            // that were already cached and would otherwise stay out-of-date.
            if (scheduleMode == SCHEDULE_INITIAL) {
                propagateToNeighbours(tile);
            }
            return tile;
        });
        if (PENDING.putIfAbsent(key, task) == null) {
            BUILD_EXECUTOR.submit(task);
        }
    }

    /**
     * Synchronous build : pit-fills the padded heightmap, computes flow direction and
     * accumulation (with optional inbound flux from cached neighbors) then crops everything
     * back to the central tile and extracts outgoing flux for the next stitching round.
     */
    public static HydrologyTile build(int originBlockI, int originBlockJ,
                                      int tileI, int tileJ,
                                      HeightmapData paddedData,
                                      FlowAccumulation.InboundFlux inbound) {
        int paddedWidth = paddedData.width;
        int paddedHeight = paddedData.height;
        short[][] paddedHeightmap = paddedData.heightmap;

        short[][] paddedFilled = PitFiller.fill(paddedHeightmap, paddedWidth, paddedHeight);
        byte[] paddedDir = FlowDirection.compute(paddedFilled, paddedWidth, paddedHeight);

        // Pad-relative inbound flux : the neighbor edges live at row/col PADDING in the padded
        // space ; not at row/col 0. We expand the inbound arrays into padded-aligned versions.
        FlowAccumulation.InboundFlux paddedInbound = expandInboundForPadding(inbound,
                paddedWidth, paddedHeight);
        int[] paddedAccum = FlowAccumulation.compute(paddedDir, paddedWidth, paddedHeight, paddedInbound);

        // Crop to central TILE_SIZE x TILE_SIZE.
        short[][] heightmap = new short[TILE_SIZE][TILE_SIZE];
        short[][] filledHeightmap = new short[TILE_SIZE][TILE_SIZE];
        byte[] flowDirection = new byte[TILE_SIZE * TILE_SIZE];
        int[] flowAccumulation = new int[TILE_SIZE * TILE_SIZE];

        for (int row = 0; row < TILE_SIZE; row++) {
            int paddedRow = row + PADDING;
            for (int col = 0; col < TILE_SIZE; col++) {
                int paddedCol = col + PADDING;
                heightmap[row][col] = paddedHeightmap[paddedRow][paddedCol];
                filledHeightmap[row][col] = paddedFilled[paddedRow][paddedCol];
                int paddedIdx = paddedRow * paddedWidth + paddedCol;
                int croppedIdx = row * TILE_SIZE + col;
                flowDirection[croppedIdx] = paddedDir[paddedIdx];
                flowAccumulation[croppedIdx] = paddedAccum[paddedIdx];
            }
        }

        FlowAccumulation.OutgoingFlux out = FlowAccumulation.extractOutgoingFluxes(
                flowDirection, flowAccumulation, TILE_SIZE, TILE_SIZE);

        byte[] features = HydrologyClassifier.classify(flowDirection, flowAccumulation,
                heightmap, TILE_SIZE, TILE_SIZE);

        return new HydrologyTile(originBlockI, originBlockJ, tileI, tileJ,
                TILE_SIZE, TILE_SIZE,
                heightmap, filledHeightmap, flowDirection, flowAccumulation,
                out.toNorth, out.toSouth, out.toWest, out.toEast,
                features);
    }

    /**
     * Convenience overload used internally that defers padded-inflow expansion to the caller.
     * Public so external callers can opt into stitching even without a cache.
     */
    public static HydrologyTile build(int originBlockI, int originBlockJ,
                                      int tileI, int tileJ, HeightmapData paddedData) {
        return build(originBlockI, originBlockJ, tileI, tileJ, paddedData,
                FlowAccumulation.InboundFlux.NONE);
    }

    /**
     * Looks at the 4 neighbor tiles in the cache and packages their outgoing flux toward us as
     * an InboundFlux. Missing neighbors contribute null arrays (no inflow on that side).
     */
    private static FlowAccumulation.InboundFlux collectInboundFluxes(int tileI, int tileJ) {
        HydrologyTile north = peekCached(tileI - 1, tileJ);
        HydrologyTile south = peekCached(tileI + 1, tileJ);
        HydrologyTile west  = peekCached(tileI, tileJ - 1);
        HydrologyTile east  = peekCached(tileI, tileJ + 1);

        return new FlowAccumulation.InboundFlux(
                north != null ? copyOf(north.outgoingFluxSouth) : null,
                south != null ? copyOf(south.outgoingFluxNorth) : null,
                west  != null ? copyOf(west.outgoingFluxEast)   : null,
                east  != null ? copyOf(east.outgoingFluxWest)   : null);
    }

    /**
     * Extends per-tile inbound flux arrays (length {@code TILE_SIZE}) so they line up with the
     * padded grid (length {@code TILE_SIZE + 2 * PADDING}) used during the actual computation.
     * The flux is injected at the padded grid's first/last row/col (i.e. the outermost padded
     * cells) because the neighbor tile's edge sits exactly there once it account for padding.
     */
    private static FlowAccumulation.InboundFlux expandInboundForPadding(
            FlowAccumulation.InboundFlux inbound, int paddedWidth, int paddedHeight) {
        int[] paddedNorth = inbound.fromNorth == null ? null : embedAlongCols(inbound.fromNorth, paddedWidth);
        int[] paddedSouth = inbound.fromSouth == null ? null : embedAlongCols(inbound.fromSouth, paddedWidth);
        int[] paddedWest  = inbound.fromWest  == null ? null : embedAlongRows(inbound.fromWest,  paddedHeight);
        int[] paddedEast  = inbound.fromEast  == null ? null : embedAlongRows(inbound.fromEast,  paddedHeight);
        return new FlowAccumulation.InboundFlux(paddedNorth, paddedSouth, paddedWest, paddedEast);
    }

    private static int[] embedAlongCols(int[] tileEdge, int paddedWidth) {
        int[] padded = new int[paddedWidth];
        // tileEdge has length TILE_SIZE ; copy to columns [PADDING, PADDING + TILE_SIZE).
        System.arraycopy(tileEdge, 0, padded, PADDING, tileEdge.length);
        return padded;
    }

    private static int[] embedAlongRows(int[] tileEdge, int paddedHeight) {
        int[] padded = new int[paddedHeight];
        System.arraycopy(tileEdge, 0, padded, PADDING, tileEdge.length);
        return padded;
    }

    private static int[] copyOf(int[] src) {
        return java.util.Arrays.copyOf(src, src.length);
    }

    /**
     * After T finishes -> examine the flux T sends to each neighbor. If a cached neighbor exists
     * and would receive significant flux (above {@link #STITCH_FLUX_THRESHOLD}) that it didn't
     * have at its own build time ; evict it and reschedule its build in restitch mode (no further
     * cascade).
     */
    private static void propagateToNeighbours(HydrologyTile tile) {
        if (sumAbove(tile.outgoingFluxNorth, STITCH_FLUX_THRESHOLD)) {
            invalidateAndReschedule(tile.tileI - 1, tile.tileJ);
        }
        if (sumAbove(tile.outgoingFluxSouth, STITCH_FLUX_THRESHOLD)) {
            invalidateAndReschedule(tile.tileI + 1, tile.tileJ);
        }
        if (sumAbove(tile.outgoingFluxWest, STITCH_FLUX_THRESHOLD)) {
            invalidateAndReschedule(tile.tileI, tile.tileJ - 1);
        }
        if (sumAbove(tile.outgoingFluxEast, STITCH_FLUX_THRESHOLD)) {
            invalidateAndReschedule(tile.tileI, tile.tileJ + 1);
        }
    }

    /** True if any value in the array is at least {@code threshold}. */
    private static boolean sumAbove(int[] flux, int threshold) {
        for (int value : flux) if (value >= threshold) return true;
        return false;
    }

    private static void invalidateAndReschedule(int tileI, int tileJ) {
        long key = tileKey(tileI, tileJ);
        synchronized (CACHE_LOCK) {
            HydrologyTile existing = CACHE.remove(key);
            if (existing == null) return; // Neighbor not cached, nothing to restitch.
        }
        scheduleBuild(key, SCHEDULE_RESTITCH);
    }

    private static HydrologyTile peekCached(int tileI, int tileJ) {
        long key = tileKey(tileI, tileJ);
        synchronized (CACHE_LOCK) {
            return CACHE.get(key);
        }
    }

    private static long tileKey(int tileI, int tileJ) {
        return ((long) tileI << 32) | (tileJ & 0xFFFFFFFFL);
    }

    private static void evictLruTo(int maxSize) {
        Iterator<Map.Entry<Long, HydrologyTile>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > maxSize && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}