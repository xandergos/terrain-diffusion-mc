package com.github.xandergos.terraindiffusionmc.infinitetensor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A lazy, sliding-window "infinite" tensor backed by a {@link MemoryTileStore}.
 *
 * <p>Only the <em>direct</em> cache strategy is implemented: each computed window
 * output is stored in an LRU cache keyed by window index.  Overlapping windows
 * are summed to produce the final slice.
 *
 * <p>Create instances exclusively through {@link MemoryTileStore#getOrCreate}.
 *
 * <p>Thread safety: concurrent {@code getSlice} calls deduplicate per-window compute
 * via an in-progress map of {@link CompletableFuture}; the second arrival joins the
 * first arrival's future rather than recomputing.
 */
public class InfiniteTensor {

    final String id;

    /** Shape in each dimension; null = unbounded. */
    final Integer[] shape;

    /** Defines position and size of each output window. */
    final TensorWindow outputWindow;

    /** Non-batched compute function (null if batched). */
    final TensorFunction function;

    /** Batched compute function (null if non-batched). */
    final BatchTensorFunction batchFunction;

    /** Maximum number of windows per batch call (0 = non-batched). */
    final int batchSize;

    /** Upstream dependency tensors. */
    final InfiniteTensor[] deps;

    /** How to slice each dependency for a given window index. */
    final TensorWindow[] depWindows;

    /** Owning store — used for cache reads/writes and dependency resolution. */
    final MemoryTileStore store;

    /**
     * Soft limit on cached window bytes.  {@code Long.MAX_VALUE} = unlimited.
     * Eviction occurs after a new window is written.
     */
    final long cacheLimitBytes;

    /** Per-window in-progress futures so concurrent compute requests dedup. */
    private final ConcurrentHashMap<List<Integer>, CompletableFuture<Void>> inProgress = new ConcurrentHashMap<>();

    InfiniteTensor(
            String id,
            Integer[] shape,
            TensorWindow outputWindow,
            TensorFunction function,
            BatchTensorFunction batchFunction,
            int batchSize,
            InfiniteTensor[] deps,
            TensorWindow[] depWindows,
            MemoryTileStore store,
            long cacheLimitBytes) {
        this.id = id;
        this.shape = shape;
        this.outputWindow = outputWindow;
        this.function = function;
        this.batchFunction = batchFunction;
        this.batchSize = batchSize;
        this.deps = deps;
        this.depWindows = depWindows;
        this.store = store;
        this.cacheLimitBytes = cacheLimitBytes;
    }

    /**
     * Retrieve a contiguous slice of this tensor.
     *
     * @param start inclusive pixel start per dimension
     * @param end   exclusive pixel end per dimension
     * @return the accumulated FloatTensor
     */
    public FloatTensor getSlice(int[] start, int[] end) {
        int n = shape.length;
        int[][] pixelRange = buildRange(start, end);

        ensureComputed(pixelRange);

        // Accumulate contributions from all intersecting windows.
        int[] outShape = new int[n];
        for (int d = 0; d < n; d++) outShape[d] = end[d] - start[d];
        FloatTensor output = new FloatTensor(outShape);

        int[] lo = outputWindow.getLowestIntersection(pixelRange);
        int[] hi = outputWindow.getHighestIntersection(pixelRange);

        iterateWindows(lo, hi, windowIndex -> {
            FloatTensor cached = store.getCachedWindow(id, windowIndex);
            if (cached == null) return;

            int[][] wBounds = outputWindow.getBounds(windowIndex);

            // Intersection of the window bounds with the requested pixel range.
            int[][] isect = new int[n][2];
            for (int d = 0; d < n; d++) {
                isect[d][0] = Math.max(pixelRange[d][0], wBounds[d][0]);
                isect[d][1] = Math.min(pixelRange[d][1], wBounds[d][1]);
                if (isect[d][0] >= isect[d][1]) return; // no overlap
            }

            int[][] srcRegion = new int[n][2];
            int[][] dstRegion = new int[n][2];
            for (int d = 0; d < n; d++) {
                srcRegion[d][0] = isect[d][0] - wBounds[d][0];
                srcRegion[d][1] = isect[d][1] - wBounds[d][0];
                dstRegion[d][0] = isect[d][0] - pixelRange[d][0];
                dstRegion[d][1] = isect[d][1] - pixelRange[d][0];
            }

            output.addFrom(cached, dstRegion, srcRegion);
        });

        return output;
    }

    /**
     * Ensures every window intersecting {@code pixelRange} is present in the cache.
     * Recursively ensures upstream dependencies are computed first.
     */
    void ensureComputed(int[][] pixelRange) {
        ensureComputedRanges(Collections.singletonList(pixelRange));
    }

    /**
     * Ensures every window that intersects any of the given pixel ranges is present.
     * Matches Python _apply_f_range: each range is expanded to window indices, then
     * deduped (no bounding-box union, so we only request windows that actually intersect
     * at least one range).
     *
     * <p>Concurrent callers are deduplicated per-window: each pending window is claimed
     * by exactly one thread (which computes it); other threads wait on the claimer's
     * future. Strictly DAG dep graph means no cycles; rigorous try/finally ensures that
     * a compute exception fails the future rather than stranding waiters.
     */
    void ensureComputedRanges(List<int[][]> pixelRanges) {
        Set<List<Integer>> pendingSet = new LinkedHashSet<>();
        for (int[][] range : pixelRanges) {
            int[] lo = outputWindow.getLowestIntersection(range);
            int[] hi = outputWindow.getHighestIntersection(range);
            iterateWindows(lo, hi, wi -> {
                if (!store.isWindowCached(id, wi)) {
                    List<Integer> key = new ArrayList<>(wi.length);
                    for (int v : wi) key.add(v);
                    pendingSet.add(key);
                }
            });
        }
        if (pendingSet.isEmpty()) return;

        // Claim per-window ownership via the in-progress map. Each window goes to exactly
        // one thread; others wait on the claimer's future.
        List<int[]> myWindows = new ArrayList<>();
        List<CompletableFuture<Void>> myFutures = new ArrayList<>();
        List<CompletableFuture<Void>> waitOnOthers = new ArrayList<>();
        for (List<Integer> key : pendingSet) {
            // Re-check the cache: another thread may have just finished computing.
            int[] wi = key.stream().mapToInt(Integer::intValue).toArray();
            if (store.isWindowCached(id, wi)) continue;

            CompletableFuture<Void> mine = new CompletableFuture<>();
            CompletableFuture<Void> existing = inProgress.putIfAbsent(key, mine);
            if (existing == null) {
                myWindows.add(wi);
                myFutures.add(mine);
            } else {
                waitOnOthers.add(existing);
            }
        }

        try {
            if (!myWindows.isEmpty()) {
                // Recurse into deps for the windows we own.
                for (int i = 0; i < deps.length; i++) {
                    List<int[][]> depRanges = new ArrayList<>(myWindows.size());
                    for (int[] wi : myWindows) {
                        depRanges.add(depWindows[i].getBounds(wi));
                    }
                    deps[i].ensureComputedRanges(depRanges);
                }

                if (batchSize > 0 && batchFunction != null) {
                    computeBatched(myWindows);
                } else {
                    for (int[] windowIndex : myWindows) {
                        computeSingle(windowIndex);
                    }
                }
            }

            // Mark all my windows complete.
            for (int i = 0; i < myWindows.size(); i++) {
                List<Integer> key = toKey(myWindows.get(i));
                inProgress.remove(key);
                myFutures.get(i).complete(null);
            }
        } catch (Throwable t) {
            // Fail my futures so waiters don't hang. Strip from inProgress so future
            // callers can retry.
            for (int i = 0; i < myWindows.size(); i++) {
                List<Integer> key = toKey(myWindows.get(i));
                inProgress.remove(key);
                myFutures.get(i).completeExceptionally(t);
            }
            throw t;
        }

        // Wait for windows another thread is computing. join() rethrows their exception
        // wrapped in CompletionException; let it propagate.
        for (CompletableFuture<Void> f : waitOnOthers) {
            f.join();
        }
    }

    private void computeSingle(int[] windowIndex) {
        List<FloatTensor> args = new ArrayList<>(deps.length);
        for (int i = 0; i < deps.length; i++) {
            int[][] bounds = depWindows[i].getBounds(windowIndex);
            int[] depStart = new int[bounds.length];
            int[] depEnd   = new int[bounds.length];
            for (int d = 0; d < bounds.length; d++) {
                depStart[d] = bounds[d][0];
                depEnd[d]   = bounds[d][1];
            }
            args.add(deps[i].getSlice(depStart, depEnd));
        }
        FloatTensor result = function.apply(windowIndex, args);
        validateOutputShape(result, windowIndex);
        store.cacheWindow(id, windowIndex, result, cacheLimitBytes);
    }

    private void computeBatched(List<int[]> windowIndices) {
        int from = 0;
        while (from < windowIndices.size()) {
            int to = Math.min(from + batchSize, windowIndices.size());
            List<int[]> batch = windowIndices.subList(from, to);

            // args.get(depIdx) → list of tensors for that dep, one per window
            List<List<FloatTensor>> args = new ArrayList<>(deps.length);
            for (int i = 0; i < deps.length; i++) {
                List<FloatTensor> depArgs = new ArrayList<>(batch.size());
                for (int[] windowIndex : batch) {
                    int[][] bounds = depWindows[i].getBounds(windowIndex);
                    int[] depStart = new int[bounds.length];
                    int[] depEnd   = new int[bounds.length];
                    for (int d = 0; d < bounds.length; d++) {
                        depStart[d] = bounds[d][0];
                        depEnd[d]   = bounds[d][1];
                    }
                    depArgs.add(deps[i].getSlice(depStart, depEnd));
                }
                args.add(depArgs);
            }

            List<FloatTensor> outputs = batchFunction.apply(batch, args);
            for (int k = 0; k < batch.size(); k++) {
                FloatTensor result = outputs.get(k);
                int[] windowIndex = batch.get(k);
                validateOutputShape(result, windowIndex);
                store.cacheWindow(id, windowIndex, result, cacheLimitBytes);
            }

            from = to;
        }
    }

    private void validateOutputShape(FloatTensor result, int[] windowIndex) {
        int n = outputWindow.size.length;
        if (result.shape.length != n) {
            throw new IllegalStateException(
                    "Function for tensor '" + id + "' returned shape with " +
                    result.shape.length + " dims, expected " + n);
        }
        for (int d = 0; d < n; d++) {
            if (result.shape[d] != outputWindow.size[d]) {
                throw new IllegalStateException(
                        "Function for tensor '" + id + "' returned shape[" + d + "]=" +
                        result.shape[d] + ", expected " + outputWindow.size[d]);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static int[][] buildRange(int[] start, int[] end) {
        int n = start.length;
        int[][] range = new int[n][2];
        for (int d = 0; d < n; d++) {
            range[d][0] = start[d];
            range[d][1] = end[d];
        }
        return range;
    }

    /**
     * Iterate over all window index combinations in the inclusive range [lo, hi].
     */
    static void iterateWindows(int[] lo, int[] hi, WindowConsumer action) {
        int n = lo.length;
        for (int d = 0; d < n; d++) {
            if (lo[d] > hi[d]) return;
        }
        int[] current = lo.clone();

        outer:
        while (true) {
            action.accept(current.clone());

            // Increment like a mixed-radix counter (last dim first).
            for (int d = n - 1; d >= 0; d--) {
                current[d]++;
                if (current[d] <= hi[d]) break;
                current[d] = lo[d];
                if (d == 0) break outer;
            }
        }
    }

    private static List<Integer> toKey(int[] windowIndex) {
        List<Integer> key = new ArrayList<>(windowIndex.length);
        for (int v : windowIndex) key.add(v);
        return key;
    }

    @FunctionalInterface
    interface WindowConsumer {
        void accept(int[] windowIndex);
    }
}
