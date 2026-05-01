package com.github.xandergos.terraindiffusionmc.infinitetensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone stress test for the concurrent {@link MemoryTileStore} +
 * {@link InfiniteTensor} dedup path.
 *
 * <p>Builds a 3-layer DAG of cheap synthetic tensors and hammers it from many threads
 * with overlapping pixel ranges. Asserts:
 * <ul>
 *   <li>No exceptions / no hung futures.</li>
 *   <li>Each window function is invoked exactly once across all threads (proves dedup).</li>
 *   <li>All threads produce byte-identical slice outputs (proves cache integrity under
 *       concurrent access-order LRU mutation).</li>
 * </ul>
 *
 * <p>Run with the {@code main} method; exits non-zero on failure.
 */
public final class MemoryTileStoreConcurrencyTest {

    private MemoryTileStoreConcurrencyTest() {}

    private static final int LAYER0_TILE = 8;
    private static final int LAYER1_TILE = 8;
    private static final int LAYER2_TILE = 8;
    private static final int LAYER0_STRIDE = 4;
    private static final int LAYER1_STRIDE = 4;
    private static final int LAYER2_STRIDE = 4;

    public static void main(String[] args) throws Exception {
        int threads = 16;
        int callsPerThread = 200;
        int seed = 42;
        long start = System.nanoTime();

        MemoryTileStore store = new MemoryTileStore();

        AtomicLong layer0Calls = new AtomicLong();
        AtomicLong layer1Calls = new AtomicLong();
        AtomicLong layer2Calls = new AtomicLong();

        // Per-window compute counters; max value must be 1 if dedup works.
        ConcurrentHashMap<String, AtomicInteger> perWindowCounts = new ConcurrentHashMap<>();

        TensorWindow l0Win = new TensorWindow(new int[]{1, LAYER0_TILE, LAYER0_TILE},
                new int[]{1, LAYER0_STRIDE, LAYER0_STRIDE});
        InfiniteTensor layer0 = store.getOrCreate(
                "layer0",
                new Integer[]{1, null, null},
                (wi, depSlices) -> {
                    layer0Calls.incrementAndGet();
                    countWindow(perWindowCounts, "layer0", wi);
                    return makeTile(wi, LAYER0_TILE, 1.0f);
                },
                l0Win,
                new InfiniteTensor[]{},
                new TensorWindow[]{},
                Long.MAX_VALUE);

        TensorWindow l1Win = new TensorWindow(new int[]{1, LAYER1_TILE, LAYER1_TILE},
                new int[]{1, LAYER1_STRIDE, LAYER1_STRIDE});
        TensorWindow l1DepWin = new TensorWindow(new int[]{1, LAYER1_TILE, LAYER1_TILE},
                new int[]{1, LAYER1_STRIDE, LAYER1_STRIDE});
        InfiniteTensor layer1 = store.getOrCreate(
                "layer1",
                new Integer[]{1, null, null},
                (wi, depSlices) -> {
                    layer1Calls.incrementAndGet();
                    countWindow(perWindowCounts, "layer1", wi);
                    return derivedTile(wi, LAYER1_TILE, depSlices.get(0), 2.0f);
                },
                l1Win,
                new InfiniteTensor[]{layer0},
                new TensorWindow[]{l1DepWin},
                Long.MAX_VALUE);

        TensorWindow l2Win = new TensorWindow(new int[]{1, LAYER2_TILE, LAYER2_TILE},
                new int[]{1, LAYER2_STRIDE, LAYER2_STRIDE});
        TensorWindow l2DepWin = new TensorWindow(new int[]{1, LAYER2_TILE, LAYER2_TILE},
                new int[]{1, LAYER2_STRIDE, LAYER2_STRIDE});
        InfiniteTensor layer2 = store.getOrCreate(
                "layer2",
                new Integer[]{1, null, null},
                (wi, depSlices) -> {
                    layer2Calls.incrementAndGet();
                    countWindow(perWindowCounts, "layer2", wi);
                    return derivedTile(wi, LAYER2_TILE, depSlices.get(0), 3.0f);
                },
                l2Win,
                new InfiniteTensor[]{layer1},
                new TensorWindow[]{l2DepWin},
                Long.MAX_VALUE);

        // Reference run on one thread to compute the canonical output for a fixed slice.
        // Concurrent threads will request overlapping but possibly larger ranges, so
        // we don't compare total compute counts. Instead, we assert below that no
        // single window was computed more than once across all threads.
        int[] refStart = new int[]{0, 0, 0};
        int[] refEnd = new int[]{1, 16, 16};
        FloatTensor reference = layer2.getSlice(refStart, refEnd);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Boolean>> futures = new ArrayList<>(threads);
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            futures.add(pool.submit(() -> {
                Random rng = new Random((long) seed * 1000003L + threadId);
                for (int i = 0; i < callsPerThread; i++) {
                    int x0 = rng.nextInt(8);
                    int y0 = rng.nextInt(8);
                    int w = 4 + rng.nextInt(8);
                    int h = 4 + rng.nextInt(8);
                    FloatTensor out = layer2.getSlice(
                            new int[]{0, y0, x0},
                            new int[]{1, y0 + h, x0 + w});
                    // Cross-check overlap with the reference where regions intersect.
                    int isectY0 = Math.max(refStart[1], y0);
                    int isectX0 = Math.max(refStart[2], x0);
                    int isectY1 = Math.min(refEnd[1], y0 + h);
                    int isectX1 = Math.min(refEnd[2], x0 + w);
                    if (isectY1 <= isectY0 || isectX1 <= isectX0) continue;
                    for (int yy = isectY0; yy < isectY1; yy++) {
                        for (int xx = isectX0; xx < isectX1; xx++) {
                            float refV = reference.data[(yy - refStart[1]) * (refEnd[2] - refStart[2])
                                    + (xx - refStart[2])];
                            float mine = out.data[(yy - y0) * w + (xx - x0)];
                            if (Float.compare(refV, mine) != 0) {
                                throw new AssertionError(String.format(
                                        "Mismatch at (%d,%d): ref=%f got=%f", xx, yy, refV, mine));
                            }
                        }
                    }
                }
                return true;
            }));
        }
        pool.shutdown();
        if (!pool.awaitTermination(2, TimeUnit.MINUTES)) {
            throw new RuntimeException("Test timed out — likely a hung future");
        }
        for (Future<Boolean> f : futures) f.get();

        long actualL0 = layer0Calls.get();
        long actualL1 = layer1Calls.get();
        long actualL2 = layer2Calls.get();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // Strong dedup invariant: no single window computed more than once.
        int maxComputeCount = 0;
        String hottestWindow = null;
        for (var e : perWindowCounts.entrySet()) {
            int v = e.getValue().get();
            if (v > maxComputeCount) {
                maxComputeCount = v;
                hottestWindow = e.getKey();
            }
        }

        System.out.printf("Threads=%d, callsPerThread=%d%n", threads, callsPerThread);
        System.out.printf("Total compute calls: layer0=%d layer1=%d layer2=%d%n",
                actualL0, actualL1, actualL2);
        System.out.printf("Unique windows computed: %d%n", perWindowCounts.size());
        System.out.printf("Max times any single window was computed: %d (%s)%n",
                maxComputeCount, hottestWindow);
        System.out.printf("Elapsed: %d ms%n", elapsedMs);
        if (maxComputeCount > 1) {
            System.err.println("FAIL: dedup broken — at least one window was computed more than once");
            System.exit(1);
        }
        System.out.println("PASS");
    }

    private static void countWindow(ConcurrentHashMap<String, AtomicInteger> counts,
                                    String layer, int[] wi) {
        StringBuilder sb = new StringBuilder(layer);
        for (int v : wi) sb.append(',').append(v);
        counts.computeIfAbsent(sb.toString(), k -> new AtomicInteger()).incrementAndGet();
    }

    /**
     * Build a deterministic 1×TILE×TILE tile whose values depend only on the window
     * index, so callers can verify caching by comparing values across reruns.
     */
    private static FloatTensor makeTile(int[] wi, int tile, float coeff) {
        FloatTensor t = new FloatTensor(new int[]{1, tile, tile});
        for (int r = 0; r < tile; r++) {
            for (int c = 0; c < tile; c++) {
                t.data[r * tile + c] = coeff * (wi[1] * 31 + wi[2] + r * 0.001f + c * 0.0001f);
            }
        }
        return t;
    }

    /** Derive a tile from a parent slice by summing parent + window-index-specific bias. */
    private static FloatTensor derivedTile(int[] wi, int tile, FloatTensor parentSlice, float coeff) {
        FloatTensor t = new FloatTensor(new int[]{1, tile, tile});
        float bias = coeff * (wi[1] * 17 + wi[2]);
        for (int r = 0; r < tile; r++) {
            for (int c = 0; c < tile; c++) {
                int pH = parentSlice.shape[1];
                int pW = parentSlice.shape[2];
                int srcR = Math.min(pH - 1, r);
                int srcC = Math.min(pW - 1, c);
                t.data[r * tile + c] = parentSlice.data[srcR * pW + srcC] + bias;
            }
        }
        return t;
    }
}
