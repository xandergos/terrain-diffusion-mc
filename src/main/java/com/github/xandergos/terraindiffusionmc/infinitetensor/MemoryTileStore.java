package com.github.xandergos.terraindiffusionmc.infinitetensor;

import java.util.*;

/**
 * In-memory factory and LRU cache for {@link InfiniteTensor} window outputs.
 *
 * <p>Each tensor registered with this store maintains its own ordered map of
 * window index → FloatTensor, with LRU eviction governed by the per-tensor
 * cache limit supplied at creation time.
 */
public class MemoryTileStore {

    /** Window cache per tensor id: access-order LinkedHashMap for LRU. */
    private final Map<String, LinkedHashMap<List<Integer>, FloatTensor>> windowCaches = new HashMap<>();

    /** Tracked byte count per tensor id. */
    private final Map<String, long[]> cacheSizes = new HashMap<>();

    /** All registered tensor instances, by id. */
    private final Map<String, InfiniteTensor> tensors = new HashMap<>();

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

        if (tensors.containsKey(id)) return tensors.get(id);

        InfiniteTensor tensor = new InfiniteTensor(
                id, shape, outputWindow, function, null, 0,
                deps, depWindows, this, cacheLimitBytes);
        register(id, tensor);
        return tensor;
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

        if (tensors.containsKey(id)) return tensors.get(id);

        InfiniteTensor tensor = new InfiniteTensor(
                id, shape, outputWindow, null, batchFunction, batchSize,
                deps, depWindows, this, cacheLimitBytes);
        register(id, tensor);
        return tensor;
    }

    private void register(String id, InfiniteTensor tensor) {
        tensors.put(id, tensor);
        // access-order LinkedHashMap for LRU eviction
        windowCaches.put(id, new LinkedHashMap<>(16, 0.75f, true));
        cacheSizes.put(id, new long[]{0L});
    }

    // -------------------------------------------------------------------------
    // Cache operations (called from InfiniteTensor)
    // -------------------------------------------------------------------------

    void cacheWindow(String id, int[] windowIndex, FloatTensor output) {
        List<Integer> key = toKey(windowIndex);
        LinkedHashMap<List<Integer>, FloatTensor> cache = windowCaches.get(id);
        long[] size = cacheSizes.get(id);

        if (cache.containsKey(key)) {
            // Already present; move to end (most-recent).
            cache.get(key); // triggers access-order promotion
            return;
        }

        cache.put(key, output);
        size[0] += output.byteSize();
    }

    void evictIfNeeded(String id, long limitBytes) {
        if (limitBytes == Long.MAX_VALUE) return;
        LinkedHashMap<List<Integer>, FloatTensor> cache = windowCaches.get(id);
        long[] size = cacheSizes.get(id);
        if (cache == null) return;

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
        return cache.get(toKey(windowIndex));
    }

    boolean isWindowCached(String id, int[] windowIndex) {
        LinkedHashMap<List<Integer>, FloatTensor> cache = windowCaches.get(id);
        return cache != null && cache.containsKey(toKey(windowIndex));
    }

    /** Remove all cached window outputs for every registered tensor. */
    public void clearAllCaches() {
        for (Map.Entry<String, LinkedHashMap<List<Integer>, FloatTensor>> e : windowCaches.entrySet()) {
            e.getValue().clear();
            cacheSizes.get(e.getKey())[0] = 0L;
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
