package com.github.xandergos.terraindiffusionmc.basin;

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
 * Builds {@link IrrigationBasinMesh} tiles from terrain heightmaps.
 *
 * <p>Tiles are aligned on a {@link #TILE_SIZE}-block grid and cached in an LRU map capped at
 * {@code MAX_CACHE_SIZE} entries. Builds run on a dedicated daemon thread so the render thread
 * never blocks ; {@link #getOrSchedule} returns {@code null} for tiles still in progress.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class IrrigationBasinBuilder {

    /** Block-space tile side length ; tiles are aligned on multiples of this value. */
    public static final int TILE_SIZE = 256;

    private static final int MAX_CACHE_SIZE = 16;
    private static final Object CACHE_LOCK = new Object();
    private static final Map<Long, IrrigationBasinMesh> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Long, Future<IrrigationBasinMesh>> PENDING = new ConcurrentHashMap<>();
    private static final ExecutorService BUILD_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "irrigation-basin-builder");
                thread.setDaemon(true);
                return thread;
            });

    private IrrigationBasinBuilder() {
    }

    /**
     * Returns the cached mesh for the tile at the given block-space origin
     * or {@code null} if it is not yet built. Schedules an async build when the tile is missing.
     */
    public static IrrigationBasinMesh getOrSchedule(int blockI, int blockJ) {
        long key = tileKey(Math.floorDiv(blockI, TILE_SIZE), Math.floorDiv(blockJ, TILE_SIZE));
        synchronized (CACHE_LOCK) {
            IrrigationBasinMesh cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        scheduleBuild(key);
        return null;
    }

    /**
     * Clears all cached and pending meshes. Call when the world or seed changes.
     */
    public static void clearCache() {
        synchronized (CACHE_LOCK) {
            CACHE.clear();
        }
        PENDING.clear();
    }

    private static void scheduleBuild(long key) {
        if (PENDING.containsKey(key)) return;

        int tileI = (int) (key >> 32);
        int tileJ = (int) key;
        int originBlockI = tileI * TILE_SIZE;
        int originBlockJ = tileJ * TILE_SIZE;

        FutureTask<IrrigationBasinMesh> task = new FutureTask<>(() -> {
            HeightmapData heightmapData = LocalTerrainProvider.getInstance()
                    .fetchHeightmap(originBlockI, originBlockJ,
                            originBlockI + TILE_SIZE, originBlockJ + TILE_SIZE);
            IrrigationBasinMesh mesh = build(originBlockI, originBlockJ, heightmapData);
            synchronized (CACHE_LOCK) {
                CACHE.put(key, mesh);
                evictLruTo(MAX_CACHE_SIZE);
            }
            PENDING.remove(key);
            return mesh;
        });
        if (PENDING.putIfAbsent(key, task) == null) {
            BUILD_EXECUTOR.submit(task);
        }
    }

    /**
     * Synchronous build : segments the heightmap and computes per-basin properties.
     *
     * <p>Public so tests and the future Phase 2 flow router can call it directly.
     */
    public static IrrigationBasinMesh build(int originBlockI, int originBlockJ, HeightmapData heightmapData) {
        int width = heightmapData.width;
        int height = heightmapData.height;
        short[][] heightmap = heightmapData.heightmap;

        LaplacianBasinSegmenter.BasinSegmentation segmentation =
                LaplacianBasinSegmenter.segment(heightmap, width, height);
        IrrigationBasin[] basins = computeBasinProperties(segmentation, heightmap);

        return new IrrigationBasinMesh(originBlockI, originBlockJ, width, height,
                heightmap, segmentation.labels, basins);
    }

    /**
     * Aggregates per-basin bounding box mean elevation and cell count in a single pass.
     */
    private static IrrigationBasin[] computeBasinProperties(
            LaplacianBasinSegmenter.BasinSegmentation segmentation, short[][] heightmap) {
        int basinCount = segmentation.basinCount;
        int width = segmentation.width;
        int height = segmentation.height;
        int[] labels = segmentation.labels;

        int[] cellCounts = new int[basinCount];
        long[] elevationSums = new long[basinCount];
        int[] minRows = new int[basinCount];
        int[] minCols = new int[basinCount];
        int[] maxRows = new int[basinCount];
        int[] maxCols = new int[basinCount];
        java.util.Arrays.fill(minRows, Integer.MAX_VALUE);
        java.util.Arrays.fill(minCols, Integer.MAX_VALUE);
        java.util.Arrays.fill(maxRows, Integer.MIN_VALUE);
        java.util.Arrays.fill(maxCols, Integer.MIN_VALUE);

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int label = labels[row * width + col];
                if (label == LaplacianBasinSegmenter.LABEL_RIDGE) continue;
                cellCounts[label]++;
                elevationSums[label] += heightmap[row][col];
                if (row < minRows[label]) minRows[label] = row;
                if (col < minCols[label]) minCols[label] = col;
                if (row > maxRows[label]) maxRows[label] = row;
                if (col > maxCols[label]) maxCols[label] = col;
            }
        }

        IrrigationBasin[] basins = new IrrigationBasin[basinCount];
        for (int basinId = 0; basinId < basinCount; basinId++) {
            float averageElevation = cellCounts[basinId] == 0 ? 0f
                    : (float) elevationSums[basinId] / cellCounts[basinId];
            basins[basinId] = IrrigationBasin.phase1(basinId, cellCounts[basinId], averageElevation,
                    minRows[basinId], minCols[basinId], maxRows[basinId], maxCols[basinId]);
        }
        return basins;
    }

    private static long tileKey(int tileI, int tileJ) {
        return ((long) tileI << 32) | (tileJ & 0xFFFFFFFFL);
    }

    private static void evictLruTo(int maxSize) {
        Iterator<Map.Entry<Long, IrrigationBasinMesh>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > maxSize && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
