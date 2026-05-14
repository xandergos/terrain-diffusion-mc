package com.github.xandergos.terraindiffusionmc.client.debug;

import com.github.xandergos.terraindiffusionmc.debug.TerrainBaseTile;
import com.github.xandergos.terraindiffusionmc.debug.cost.TerrainCostBuilder;
import com.github.xandergos.terraindiffusionmc.debug.cost.TerrainCostTile;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowBuilder;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowTile;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverBuilder;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverTile;
import com.github.xandergos.terraindiffusionmc.debug.river.overlay.TerrainRiverChunkOverlay;
import com.github.xandergos.terraindiffusionmc.debug.river.overlay.TerrainRiverChunkOverlayBuilder;
import com.github.xandergos.terraindiffusionmc.debug.river.overlay.TerrainRiverChunkOverlayConfig;
import com.github.xandergos.terraindiffusionmc.debug.river.global.GlobalRiverNetworkProvider;
import com.github.xandergos.terraindiffusionmc.debug.river.global.RiverNetwork;
import com.github.xandergos.terraindiffusionmc.debug.river.global.RiverRegionKey;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverNetwork;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverSpatialIndex;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverVectorBuilder;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverVectorConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TerrainDebugTileClientCache {
    private static final int MAX_ENTRIES = 64;
    private static final int COST_HALO_BLOCKS = 2;

    // Accumulation and river extraction are still debug-local, but this halo avoids the
    // worst tile-edge blindness while keeping generation responsive enough for overlay use.
    private static final int FLOW_HALO_BLOCKS = 96;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "terrain-debug-overlay-loader");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<Key, CompletableFuture<TerrainBaseTile>> FUTURES = new ConcurrentHashMap<>();
    private static final Map<Key, CompletableFuture<TerrainCostTile>> COST_FUTURES = new ConcurrentHashMap<>();
    private static final Map<Key, CompletableFuture<TerrainFlowTile>> FLOW_FUTURES = new ConcurrentHashMap<>();
    private static final Map<Key, CompletableFuture<TerrainRiverTile>> RIVER_FUTURES = new ConcurrentHashMap<>();
    private static final Map<Key, CompletableFuture<TerrainRiverNetwork>> RIVER_VECTOR_FUTURES = new ConcurrentHashMap<>();
    private static final Map<Key, CompletableFuture<TerrainRiverSpatialIndex>> RIVER_SPATIAL_INDEX_FUTURES = new ConcurrentHashMap<>();
    private static final Map<Key, CompletableFuture<TerrainRiverChunkOverlay>> RIVER_CHUNK_OVERLAY_FUTURES = new ConcurrentHashMap<>();
    private static final Map<GlobalRiverKey, CompletableFuture<RiverNetwork>> GLOBAL_RIVER_NETWORK_FUTURES = new ConcurrentHashMap<>();
    private static final GlobalRiverNetworkProvider GLOBAL_RIVER_PROVIDER = GlobalRiverNetworkProvider.createDefault();

    private TerrainDebugTileClientCache() {
    }

    public static void request(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return;
        }

        Key key = currentKey(blockStartZ, blockStartX, sizeZ, sizeX);
        FUTURES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> LocalTerrainProvider.getInstance()
                .fetchTerrainBaseTile(blockStartZ, blockStartX, blockStartZ + sizeZ, blockStartX + sizeX), EXECUTOR));

        trimCompletedEntriesIfNeeded(FUTURES);
    }

    public static TerrainBaseTile getIfReady(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return null;
        }

        CompletableFuture<TerrainBaseTile> future = FUTURES.get(currentKey(blockStartZ, blockStartX, sizeZ, sizeX));
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        return future.getNow(null);
    }

    public static void requestCost(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return;
        }

        Key key = currentKey(blockStartZ, blockStartX, sizeZ, sizeX);
        COST_FUTURES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
            int halo = COST_HALO_BLOCKS;
            TerrainBaseTile expanded = LocalTerrainProvider.getInstance().fetchTerrainBaseTile(
                    blockStartZ - halo,
                    blockStartX - halo,
                    blockStartZ + sizeZ + halo,
                    blockStartX + sizeX + halo
            );

            return TerrainCostBuilder.buildCropped(expanded, blockStartX, blockStartZ, sizeX, sizeZ);
        }, EXECUTOR));

        trimCompletedEntriesIfNeeded(COST_FUTURES);
    }

    public static TerrainCostTile getCostIfReady(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return null;
        }

        CompletableFuture<TerrainCostTile> future = COST_FUTURES.get(currentKey(blockStartZ, blockStartX, sizeZ, sizeX));
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        return future.getNow(null);
    }

    public static void requestFlow(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return;
        }

        Key key = currentKey(blockStartZ, blockStartX, sizeZ, sizeX);
        FLOW_FUTURES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> buildFlowTile(blockStartZ, blockStartX, sizeZ, sizeX), EXECUTOR));

        trimCompletedEntriesIfNeeded(FLOW_FUTURES);
    }

    public static TerrainFlowTile getFlowIfReady(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return null;
        }

        CompletableFuture<TerrainFlowTile> future = FLOW_FUTURES.get(currentKey(blockStartZ, blockStartX, sizeZ, sizeX));
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        return future.getNow(null);
    }

    public static void requestRiver(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return;
        }

        Key key = currentKey(blockStartZ, blockStartX, sizeZ, sizeX);
        RIVER_FUTURES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
            TerrainFlowTile flow = buildFlowTile(blockStartZ, blockStartX, sizeZ, sizeX);
            return TerrainRiverBuilder.build(flow);
        }, EXECUTOR));

        trimCompletedEntriesIfNeeded(RIVER_FUTURES);
    }

    public static TerrainRiverTile getRiverIfReady(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return null;
        }

        CompletableFuture<TerrainRiverTile> future = RIVER_FUTURES.get(currentKey(blockStartZ, blockStartX, sizeZ, sizeX));
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        return future.getNow(null);
    }

    public static void requestRiverVector(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return;
        }

        Key key = currentKey(blockStartZ, blockStartX, sizeZ, sizeX);
        RIVER_VECTOR_FUTURES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> buildRiverNetwork(blockStartZ, blockStartX, sizeZ, sizeX), EXECUTOR));

        trimCompletedEntriesIfNeeded(RIVER_VECTOR_FUTURES);
    }

    public static TerrainRiverNetwork getRiverVectorIfReady(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return null;
        }

        CompletableFuture<TerrainRiverNetwork> future = RIVER_VECTOR_FUTURES.get(currentKey(blockStartZ, blockStartX, sizeZ, sizeX));
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        return future.getNow(null);
    }

    public static void requestRiverSpatialIndex(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return;
        }

        Key key = currentKey(blockStartZ, blockStartX, sizeZ, sizeX);
        RIVER_SPATIAL_INDEX_FUTURES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(
                () -> buildRiverSpatialIndex(blockStartZ, blockStartX, sizeZ, sizeX), EXECUTOR));

        trimCompletedEntriesIfNeeded(RIVER_SPATIAL_INDEX_FUTURES);
    }

    public static TerrainRiverSpatialIndex getRiverSpatialIndexIfReady(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return null;
        }

        CompletableFuture<TerrainRiverSpatialIndex> future = RIVER_SPATIAL_INDEX_FUTURES.get(currentKey(blockStartZ, blockStartX, sizeZ, sizeX));
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        return future.getNow(null);
    }

    public static void requestRiverTileOverlay(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return;
        }

        Key key = currentKey(blockStartZ, blockStartX, sizeZ, sizeX);
        RIVER_CHUNK_OVERLAY_FUTURES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(
                () -> buildRiverTileOverlay(blockStartZ, blockStartX, sizeZ, sizeX), EXECUTOR));

        trimCompletedEntriesIfNeeded(RIVER_CHUNK_OVERLAY_FUTURES);
    }

    public static TerrainRiverChunkOverlay getRiverTileOverlayIfReady(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return null;
        }

        CompletableFuture<TerrainRiverChunkOverlay> future = RIVER_CHUNK_OVERLAY_FUTURES.get(currentKey(blockStartZ, blockStartX, sizeZ, sizeX));
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        return future.getNow(null);
    }

    public static void requestRiverChunkOverlay(int chunkBlockStartZ, int chunkBlockStartX) {
        int size = TerrainRiverChunkOverlayConfig.CHUNK_SIZE_BLOCKS;
        requestRiverTileOverlay(chunkBlockStartZ, chunkBlockStartX, size, size);
    }

    public static TerrainRiverChunkOverlay getRiverChunkOverlayIfReady(int chunkBlockStartZ, int chunkBlockStartX) {
        int size = TerrainRiverChunkOverlayConfig.CHUNK_SIZE_BLOCKS;
        return getRiverTileOverlayIfReady(chunkBlockStartZ, chunkBlockStartX, size, size);
    }

    public static void requestGlobalRiverNetwork(int blockX, int blockZ) {
        if (!LocalTerrainProvider.isInitialized()) {
            return;
        }

        GlobalRiverKey key = currentGlobalRiverKey(blockX, blockZ);
        GLOBAL_RIVER_NETWORK_FUTURES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
            RiverRegionKey regionKey = RiverRegionKey.fromBlock(
                    key.seed(),
                    key.scale(),
                    blockX,
                    blockZ,
                    GlobalRiverNetworkProvider.DEFAULT_REGION_SIZE_BLOCKS
            );
            return GLOBAL_RIVER_PROVIDER.getOrBuildStitchedRegion(
                    regionKey,
                    GlobalRiverNetworkProvider.DEFAULT_STITCH_RADIUS_REGIONS
            ).network();
        }, EXECUTOR));

        trimCompletedEntriesIfNeeded(GLOBAL_RIVER_NETWORK_FUTURES);
    }

    public static RiverNetwork getGlobalRiverNetworkIfReady(int blockX, int blockZ) {
        if (!LocalTerrainProvider.isInitialized()) {
            return null;
        }

        CompletableFuture<RiverNetwork> future = GLOBAL_RIVER_NETWORK_FUTURES.get(currentGlobalRiverKey(blockX, blockZ));
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        return future.getNow(null);
    }

    public static void clear() {
        FUTURES.clear();
        COST_FUTURES.clear();
        FLOW_FUTURES.clear();
        RIVER_FUTURES.clear();
        RIVER_VECTOR_FUTURES.clear();
        RIVER_SPATIAL_INDEX_FUTURES.clear();
        RIVER_CHUNK_OVERLAY_FUTURES.clear();
        GLOBAL_RIVER_NETWORK_FUTURES.clear();
    }

    private static TerrainRiverChunkOverlay buildRiverTileOverlay(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        TerrainBaseTile terrain = LocalTerrainProvider.getInstance().fetchTerrainBaseTile(
                blockStartZ,
                blockStartX,
                blockStartZ + sizeZ,
                blockStartX + sizeX
        );

        int margin = TerrainRiverChunkOverlayConfig.LOCAL_NETWORK_MARGIN_BLOCKS;
        int networkStartZ = blockStartZ - margin;
        int networkStartX = blockStartX - margin;
        int networkSizeZ = sizeZ + margin * 2;
        int networkSizeX = sizeX + margin * 2;
        TerrainRiverSpatialIndex index = buildRiverSpatialIndex(networkStartZ, networkStartX, networkSizeZ, networkSizeX);

        return TerrainRiverChunkOverlayBuilder.build(terrain, index);
    }

    private static TerrainRiverChunkOverlay buildRiverChunkOverlay(int chunkBlockStartZ, int chunkBlockStartX) {
        int size = TerrainRiverChunkOverlayConfig.CHUNK_SIZE_BLOCKS;
        return buildRiverTileOverlay(chunkBlockStartZ, chunkBlockStartX, size, size);
    }

    private static TerrainRiverSpatialIndex buildRiverSpatialIndex(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        TerrainRiverNetwork network = buildRiverNetwork(blockStartZ, blockStartX, sizeZ, sizeX);
        return TerrainRiverSpatialIndex.build(network, TerrainRiverVectorConfig.SPATIAL_INDEX_CELL_SIZE_BLOCKS);
    }

    private static TerrainRiverNetwork buildRiverNetwork(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        TerrainFlowTile flow = buildFlowTile(blockStartZ, blockStartX, sizeZ, sizeX);
        TerrainRiverTile river = TerrainRiverBuilder.build(flow);
        return TerrainRiverVectorBuilder.build(river);
    }

    private static TerrainFlowTile buildFlowTile(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        int halo = FLOW_HALO_BLOCKS;
        TerrainBaseTile expanded = LocalTerrainProvider.getInstance().fetchTerrainBaseTile(
                blockStartZ - halo,
                blockStartX - halo,
                blockStartZ + sizeZ + halo,
                blockStartX + sizeX + halo
        );

        TerrainCostTile expandedCost = TerrainCostBuilder.build(expanded);
        return TerrainFlowBuilder.buildCropped(expanded, expandedCost, blockStartX, blockStartZ, sizeX, sizeZ);
    }

    private static GlobalRiverKey currentGlobalRiverKey(int blockX, int blockZ) {
        RiverRegionKey regionKey = RiverRegionKey.fromBlock(
                LocalTerrainProvider.getSeed(),
                WorldScaleManager.getCurrentScale(),
                blockX,
                blockZ,
                GlobalRiverNetworkProvider.DEFAULT_REGION_SIZE_BLOCKS
        );
        return new GlobalRiverKey(
                regionKey.seed(),
                regionKey.scale(),
                regionKey.regionX(),
                regionKey.regionZ(),
                regionKey.regionSizeBlocks()
        );
    }

    private static Key currentKey(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        return new Key(
                LocalTerrainProvider.getSeed(),
                WorldScaleManager.getCurrentScale(),
                blockStartZ,
                blockStartX,
                sizeZ,
                sizeX
        );
    }

    private static <K, T> void trimCompletedEntriesIfNeeded(Map<K, CompletableFuture<T>> futures) {
        if (futures.size() <= MAX_ENTRIES) {
            return;
        }

        Iterator<Map.Entry<K, CompletableFuture<T>>> iterator = futures.entrySet().iterator();
        while (iterator.hasNext() && futures.size() > MAX_ENTRIES / 2) {
            CompletableFuture<T> future = iterator.next().getValue();
            if (future.isDone() || future.isCompletedExceptionally()) {
                iterator.remove();
            }
        }
    }

    private record Key(long seed, int scale, int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
    }

    private record GlobalRiverKey(long seed, int scale, int regionX, int regionZ, int regionSizeBlocks) {
    }
}
