package com.github.xandergos.terraindiffusionmc.infinitetensor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory factory and LRU cache for {@link InfiniteTensor} window outputs.
 *
 * <p>Each tensor registered with this store maintains its own ordered map of
 * window index → FloatTensor, with LRU eviction governed by the per-tensor
 * cache limit supplied at creation time.
 *
 * <p>Thread safety: top-level registries use {@link ConcurrentHashMap}. Per-tensor
 * cache mutations and reads (the access-order {@link LinkedHashMap} mutates on get)
 * are synchronized on the per-tensor cache map.
 */
public class MemoryTileStore {

    /** Window cache per tensor id: access-order LinkedHashMap for LRU. */
    private final Map<String, LinkedHashMap<List<Integer>, FloatTensor>> windowCaches = new ConcurrentHashMap<>();

    /** Tracked byte count per tensor id. */
    private final Map<String, long[]> cacheSizes = new ConcurrentHashMap<>();

    /** All registered tensor instances, by id. */
    private final Map<String, InfiniteTensor> tensors = new ConcurrentHashMap<>();
    /** Monotonic count of newly computed/cached windows across all tensors. */
    private final AtomicLong totalComputedWindowCount = new AtomicLong(0L);

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a non-batched InfiniteTensor, or returns the existing one if already
     * registered under {@code id}.
     *
     * @param id            unique name for this tensor
     * @param shape         per-dimension size; {@code null} = unbounded
     * @param function      window compute function
     * @param outputWindow  sliding window specification for outputs
     * @param deps          upstream dependency tensors (may be empty)
     * @param depWindows    how to slice each dependency for a window index
     * @param cacheLimitBytes soft LRU limit; {@code Long.MAX_VALUE} = unlimited
     */
    public InfiniteTensor getOrCreate(
            String id,
            Integer[] shape,
            TensorFunction function,
            TensorWindow outputWindow,
            InfiniteTensor[] deps,
            TensorWindow[] depWindows,
            long cacheLimitBytes) {

        InfiniteTensor existing = tensors.get(id);
        if (existing != null) return existing;

        InfiniteTensor tensor = new InfiniteTensor(
                id, shape, outputWindow, function, null, 0,
                deps, depWindows, this, cacheLimitBytes);
        InfiniteTensor prior = registerIfAbsent(id, tensor);
        return prior != null ? prior : tensor;
    }

    /**
     * Creates a batched InfiniteTensor, or returns the existing one.
     *
     * @param batchSize maximum windows per {@link BatchTensorFunction} call
     */
    public InfiniteTensor getOrCreateBatched(
            String id,
            Integer[] shape,
            BatchTensorFunction batchFunction,
            TensorWindow outputWindow,
            InfiniteTensor[] deps,
            TensorWindow[] depWindows,
            long cacheLimitBytes,
            int batchSize) {

        InfiniteTensor existing = tensors.get(id);
        if (existing != null) return existing;

        InfiniteTensor tensor = new InfiniteTensor(
                id, shape, outputWindow, null, batchFunction, batchSize,
                deps, depWindows, this, cacheLimitBytes);
        InfiniteTensor prior = registerIfAbsent(id, tensor);
        return prior != null ? prior : tensor;
    }

    /**
     * Atomically registers a tensor under {@code id} and creates its cache structures.
     * Returns null if newly registered, or the existing tensor if another thread won the race.
     */
    private InfiniteTensor registerIfAbsent(String id, InfiniteTensor tensor) {
        InfiniteTensor prior = tensors.putIfAbsent(id, tensor);
        if (prior != null) return prior;
        // access-order LinkedHashMap for LRU eviction
        windowCaches.put(id, new LinkedHashMap<>(16, 0.75f, true));
        cacheSizes.put(id, new long[]{0L});
        return null;
    }

    // -------------------------------------------------------------------------
    // Cache operations (called from InfiniteTensor)
    // -------------------------------------------------------------------------

    void cacheWindow(String id, int[] windowIndex, FloatTensor output, long limitBytes) {
        List<Integer> key = toKey(windowIndex);
        LinkedHashMap<List<Integer>, FloatTensor> cache = windowCaches.get(id);
        long[] size = cacheSizes.get(id);

        synchronized (cache) {
            if (cache.containsKey(key)) {
                cache.get(key); // promote to most-recent in access-order map
                return;
            }
            cache.put(key, output);
            size[0] += output.byteSize();
            evictLocked(cache, size, limitBytes);
        }
        totalComputedWindowCount.incrementAndGet();
    }

    /** Returns how many windows have been newly computed and cached. */
    public long getTotalComputedWindowCount() {
        return totalComputedWindowCount.get();
    }

    private static void evictLocked(LinkedHashMap<List<Integer>, FloatTensor> cache,
                                    long[] size, long limitBytes) {
        if (limitBytes == Long.MAX_VALUE) return;
        // Keep at least one entry even if it exceeds the limit.
        Iterator<Map.Entry<List<Integer>, FloatTensor>> it = cache.entrySet().iterator();
        while (size[0] > limitBytes && cache.size() > 1 && it.hasNext()) {
            Map.Entry<List<Integer>, FloatTensor> entry = it.next();
            size[0] -= entry.getValue().byteSize();
            it.remove();
        }
    }

    FloatTensor getCachedWindow(String id, int[] windowIndex) {
        LinkedHashMap<List<Integer>, FloatTensor> cache = windowCaches.get(id);
        if (cache == null) return null;
        List<Integer> key = toKey(windowIndex);
        // LinkedHashMap with access-order=true mutates on get — must lock reads too.
        synchronized (cache) {
            return cache.get(key);
        }
    }

    boolean isWindowCached(String id, int[] windowIndex) {
        LinkedHashMap<List<Integer>, FloatTensor> cache = windowCaches.get(id);
        if (cache == null) return false;
        List<Integer> key = toKey(windowIndex);
        synchronized (cache) {
            return cache.containsKey(key);
        }
    }

    /** Remove all cached window outputs for every registered tensor. */
    public void clearAllCaches() {
        for (Map.Entry<String, LinkedHashMap<List<Integer>, FloatTensor>> e : windowCaches.entrySet()) {
            LinkedHashMap<List<Integer>, FloatTensor> cache = e.getValue();
            long[] size = cacheSizes.get(e.getKey());
            synchronized (cache) {
                cache.clear();
                if (size != null) size[0] = 0L;
            }
        }
    }

    /** Remove a single tensor and all its cached state. */
    public void removeTensor(String id) {
        tensors.remove(id);
        windowCaches.remove(id);
        cacheSizes.remove(id);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Integer> toKey(int[] windowIndex) {
        List<Integer> key = new ArrayList<>(windowIndex.length);
        for (int v : windowIndex) key.add(v);
        return key;
    }
}
