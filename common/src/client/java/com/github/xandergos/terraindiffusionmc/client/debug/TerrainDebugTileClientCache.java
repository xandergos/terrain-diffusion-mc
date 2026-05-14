package com.github.xandergos.terraindiffusionmc.client.debug;

import com.github.xandergos.terraindiffusionmc.debug.TerrainBaseTile;
import com.github.xandergos.terraindiffusionmc.debug.cost.TerrainCostBuilder;
import com.github.xandergos.terraindiffusionmc.debug.cost.TerrainCostTile;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowBuilder;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowTile;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverBuilder;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverTile;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverNetwork;
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

    // The river overlay is vector-based, but the hydrology still needs an expanded raster input.
    private static final int FLOW_HALO_BLOCKS = 96;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "terrain-river-debug-overlay-loader");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<Key, CompletableFuture<TerrainRiverNetwork>> RIVER_VECTOR_FUTURES = new ConcurrentHashMap<>();

    private TerrainDebugTileClientCache() {
    }

    public static void requestRiverVector(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        if (!LocalTerrainProvider.isInitialized()) {
            return;
        }

        Key key = currentKey(blockStartZ, blockStartX, sizeZ, sizeX);
        RIVER_VECTOR_FUTURES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(
                () -> buildRiverNetwork(blockStartZ, blockStartX, sizeZ, sizeX), EXECUTOR));

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

    public static void clear() {
        RIVER_VECTOR_FUTURES.clear();
    }

    private static TerrainRiverNetwork buildRiverNetwork(int blockStartZ, int blockStartX, int sizeZ, int sizeX) {
        int halo = TerrainRiverVectorConfig.LOCAL_TILE_VECTOR_HALO_BLOCKS;
        int expandedStartZ = blockStartZ - halo;
        int expandedStartX = blockStartX - halo;
        int expandedSizeZ = sizeZ + halo * 2;
        int expandedSizeX = sizeX + halo * 2;

        TerrainFlowTile flow = buildFlowTile(expandedStartZ, expandedStartX, expandedSizeZ, expandedSizeX);
        TerrainRiverTile river = TerrainRiverBuilder.build(flow);
        return TerrainRiverVectorBuilder.buildCropped(river, blockStartX, blockStartZ, sizeX, sizeZ);
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
}
