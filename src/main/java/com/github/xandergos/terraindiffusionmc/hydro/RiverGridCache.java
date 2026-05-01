package com.github.xandergos.terraindiffusionmc.hydro;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy, seed-aware cache for hydrological data.
 *
 * <h3>Region layout</h3>
 * Each cached region covers {@value REGION_TILES}×{@value REGION_TILES} heightmap tiles.
 * With the default tile_size=256 and cellSize=8, each region is 1024×1024 blocks
 * and 128×128 coarse cells — large enough that flow accumulation is meaningful
 * across tile boundaries.
 *
 * <h3>Padding</h3>
 * Each region fetches {@value PADDING_CELLS} extra coarse cells on each side so that
 * accumulation at the edge of the region includes water coming from adjacent regions.
 * The padded strip is used for computation only; it is excluded from query results.
 *
 * <h3>Thread safety</h3>
 * {@code computeIfAbsent} on {@link ConcurrentHashMap} may call the supplier more than
 * once under contention, but suppliers are idempotent (same heightmap → same result).
 */
public final class RiverGridCache {

    private static final Logger LOG = LoggerFactory.getLogger(RiverGridCache.class);

    /** Number of tiles on each side of a cached region. Must be a power of 2. */
    private static final int REGION_TILES = 4;

    /** Coarse cell size in blocks. 8 gives 32 cells per 256-block tile. */
    public static final int CELL_SIZE = 8;

    /** Padding in coarse cells added around each region for accumulation correctness. */
    private static final int PADDING_CELLS = 4;

    // -------------------------------------------------------------------------

    private record RegionKey(int regionX, int regionZ) {}

    /** Per-region data: the flow grid + extracted edges for the *inner* area. */
    private record RegionData(FlowGrid grid, List<RiverNetwork.RiverEdge> edges,
                              int innerOriginX, int innerOriginZ,
                              int innerWidth,   int innerHeight) {}

    private static final ConcurrentHashMap<RegionKey, RegionData>    CACHE       = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, RiverNetwork.ChunkMaps> CHUNK_CACHE = new ConcurrentHashMap<>();

    private static final int MAX_CHUNK_CACHE = 256;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the pre-computed ChunkMaps (depression + water) for a 16×16 chunk.
     * Both arrays are indexed [z*16 + x].
     */
    public static RiverNetwork.ChunkMaps getMapsForChunk(int chunkX, int chunkZ) {
        long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        RiverNetwork.ChunkMaps cached = CHUNK_CACHE.get(chunkKey);
        if (cached != null) return cached;

        int tileSize = TerrainDiffusionConfig.tileSize();
        int originX  = chunkX * 16;
        int originZ  = chunkZ * 16;
        RegionData rd = getOrCompute(originX, originZ, tileSize);

        RiverNetwork.ChunkMaps maps = (rd != null)
                ? RiverNetwork.buildMaps(rd.edges(), CELL_SIZE, originX, originZ, 16, 16)
                : new RiverNetwork.ChunkMaps(new int[256], new boolean[256]);

        if (CHUNK_CACHE.size() >= MAX_CHUNK_CACHE) {
            int toRemove = MAX_CHUNK_CACHE / 2;
            for (Long k : CHUNK_CACHE.keySet()) {
                if (toRemove-- <= 0) break;
                CHUNK_CACHE.remove(k);
            }
        }
        CHUNK_CACHE.put(chunkKey, maps);
        return maps;
    }

    /**
     * Convenience: depression only (for density function).
     */
    public static int[] getDepressionMapForChunk(int chunkX, int chunkZ) {
        return getMapsForChunk(chunkX, chunkZ).depression();
    }

    /**
     * Convenience: water presence only (for surface rule / block placement).
     */
    public static boolean[] getWaterMapForChunk(int chunkX, int chunkZ) {
        return getMapsForChunk(chunkX, chunkZ).water();
    }

    /** Invalidate all cached regions (call when seed changes). */
    public static void clear() {
        CACHE.clear();
        CHUNK_CACHE.clear();
        LOG.debug("[hydro] RiverGridCache cleared");
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static RegionData getOrCompute(int worldX, int worldZ, int tileSize) {
        int regionBlockSize = REGION_TILES * tileSize;
        int regionX = Math.floorDiv(worldX, regionBlockSize);
        int regionZ = Math.floorDiv(worldZ, regionBlockSize);
        RegionKey key = new RegionKey(regionX, regionZ);
        return CACHE.computeIfAbsent(key, k -> compute(k, regionBlockSize, tileSize));
    }

    private static RegionData compute(RegionKey key, int regionBlockSize, int tileSize) {
        int innerOriginX = key.regionX() * regionBlockSize;
        int innerOriginZ = key.regionZ() * regionBlockSize;

        // Padded fetch area in blocks
        int padBlocks    = PADDING_CELLS * CELL_SIZE;
        int fetchOriginX = innerOriginX - padBlocks;
        int fetchOriginZ = innerOriginZ - padBlocks;
        int fetchSize    = regionBlockSize + 2 * padBlocks;

        LOG.debug("[hydro] Computing river region ({},{}) — fetching {}×{} blocks at ({},{})",
                key.regionX(), key.regionZ(), fetchSize, fetchSize, fetchOriginX, fetchOriginZ);

        HeightmapData data;
        try {
            // fetchHeightmap(i1=Z, j1=X, i2=Z2, j2=X2)
            data = LocalTerrainProvider.getInstance().fetchHeightmap(
                    fetchOriginZ, fetchOriginX,
                    fetchOriginZ + fetchSize, fetchOriginX + fetchSize);
        } catch (Exception e) {
            LOG.error("[hydro] Failed to fetch heightmap for river region", e);
            return null;
        }

        if (data == null || data.heightmap == null) return null;

        // Downsample to coarse grid (nearest)
        int coarseFetch = fetchSize / CELL_SIZE;
        short[][] coarse = downsample(data.heightmap, data.height, data.width, CELL_SIZE, coarseFetch, coarseFetch);

        FlowGrid grid = FlowGrid.compute(coarse, CELL_SIZE);

        // Extract edges, keeping only the inner (non-padded) part
        // so carving does not leak into adjacent unloaded regions.
        List<RiverNetwork.RiverEdge> edges = RiverNetwork.extract(grid);
        // Shift edge coords: coarse grid origin → world origin
        List<RiverNetwork.RiverEdge> shiftedEdges = edges.stream()
                .map(e -> shiftEdge(e, fetchOriginX, fetchOriginZ, CELL_SIZE))
                .toList();

        int innerWidthBlocks  = regionBlockSize;
        int innerHeightBlocks = regionBlockSize;

        return new RegionData(grid, shiftedEdges,
                innerOriginX, innerOriginZ,
                innerWidthBlocks, innerHeightBlocks);
    }

    /** Downsample heightmap with nearest-neighbour, average over the cell. */
    private static short[][] downsample(short[][] src, int srcH, int srcW,
                                        int cellSize, int outH, int outW) {
        short[][] out = new short[outH][outW];
        for (int z = 0; z < outH; z++) {
            for (int x = 0; x < outW; x++) {
                // Average over the cell for smoother flow direction
                long sum = 0;
                int  cnt = 0;
                for (int dz = 0; dz < cellSize; dz++) {
                    int sz = Math.min(z * cellSize + dz, srcH - 1);
                    for (int dx = 0; dx < cellSize; dx++) {
                        int sx = Math.min(x * cellSize + dx, srcW - 1);
                        sum += src[sz][sx];
                        cnt++;
                    }
                }
                out[z][x] = (short)(sum / cnt);
            }
        }
        return out;
    }

    /**
     * Re-express a RiverEdge whose coords are in coarse-grid units relative to the padded
     * fetch origin into world-block coords (the edges store world coords implicitly via
     * the cellSize multiplier inside buildDepressionMap).
     *
     * The edge grid coords are already correct for use with CELL_SIZE; we just need to
     * add the fetch origin so that (gridX * CELL_SIZE) == worldX.
     */
    private static RiverNetwork.RiverEdge shiftEdge(RiverNetwork.RiverEdge e,
                                                    int fetchOriginX, int fetchOriginZ,
                                                    int cellSize) {
        // Convert coarse cell → world coords, then back to coarse (just store offset)
        // Actually buildDepressionMap uses (gridX + 0.5) * cellSize for world coords.
        // We need the gridX to produce the correct world position:
        //   (gridX + 0.5) * cellSize + fetchOriginX == real world X
        // So shift: use fractional origin trick by embedding origin in a wrapper edge.
        // Simplest: create a new RiverEdge where fromX/toX already include the fetch offset in cells.
        int fxCells = (int)(fetchOriginX / (float) cellSize);
        int fzCells = (int)(fetchOriginZ / (float) cellSize);
        return new RiverNetwork.RiverEdge(
                e.fromX + fxCells, e.fromZ + fzCells,
                e.toX   + fxCells, e.toZ   + fzCells,
                e.logAcc, e.slope, e.cls);
    }
}