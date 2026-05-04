package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides terrain heightmap and biome data from the local WorldPipeline.
 *
 * <p>When scale=1 the pipeline is sampled at native model resolution directly.
 * When scale>1 the pipeline is sampled at native resolution and the result is
 * bilinearly upsampled, giving 1 block = nativeResolution/scale.
 */
public final class LocalTerrainProvider {
    private static final Logger LOG = LoggerFactory.getLogger(LocalTerrainProvider.class);

    private static final float NATIVE_RESOLUTION = WorldPipelineModelConfig.nativeResolution();

    private static final FastNoiseLite ELEV_NOISE_COARSE = makeFnl(99999, 1f/24f, 3, 2f, 0.5f);
    private static final FastNoiseLite ELEV_NOISE_FINE   = makeFnl(88888, 1f/6f,  2, 2f, 0.6f);

    private static FastNoiseLite makeFnl(int seed, float freq, int oct, float lac, float gain) {
        FastNoiseLite fnl = new FastNoiseLite(seed);
        fnl.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        fnl.SetFrequency(freq);
        fnl.SetFractalType(FastNoiseLite.FractalType.FBm);
        fnl.SetFractalOctaves(oct);
        fnl.SetFractalLacunarity(lac);
        fnl.SetFractalGain(gain);
        return fnl;
    }

    public static final class HeightmapData {
        public final short[] heightmap;
        public final short[] biomeIds;
        /** Optional river render-class grid : 0 none, 1 bank, 2 tiny stream, 3 small stream or 4 full river. */
        public final byte[] riverCells;
        public final int width;
        public final int height;

        public HeightmapData(short[] heightmap, short[] biomeIds, int width, int height) {
            this(heightmap, biomeIds, null, width, height);
        }

        public HeightmapData(short[] heightmap, short[] biomeIds, byte[] riverCells, int width, int height) {
            int expected = width * height;
            if (heightmap != null && heightmap.length < expected) {
                throw new IllegalArgumentException("heightmap is smaller than width * height");
            }
            if (biomeIds != null && biomeIds.length < expected) {
                throw new IllegalArgumentException("biomeIds is smaller than width * height");
            }
            if (riverCells != null && riverCells.length < expected) {
                throw new IllegalArgumentException("riverCells is smaller than width * height");
            }
            this.heightmap  = heightmap;
            this.biomeIds   = biomeIds;
            this.riverCells = riverCells;
            this.width      = width;
            this.height     = height;
        }

        public int index(int z, int x) {
            return z * width + x;
        }

        public short heightAt(int z, int x) {
            return heightmap[index(z, x)];
        }

        public short biomeAt(int z, int x) {
            return biomeIds[index(z, x)];
        }

        public byte riverAt(int z, int x) {
            return riverCells[index(z, x)];
        }

        public long estimatedBytes() {
            return 96L
                    + (heightmap == null ? 0L : (long) heightmap.length * Short.BYTES)
                    + (biomeIds == null ? 0L : (long) biomeIds.length * Short.BYTES)
                    + (riverCells == null ? 0L : riverCells.length);
        }
    }

    private static record CacheKey(int i1, int j1, int i2, int j2) {}
    private static record CacheEntry(HeightmapData data, AtomicLong lastAccessed, long bytes) {}

    private static final long MAX_CACHE_BYTES = Long.getLong(
            "terrainDiffusion.cache.maxBytes", 128L * 1024L * 1024L);
    private static final long CACHE_EVICTION_HEADROOM_BYTES = Math.max(16L * 1024L * 1024L, MAX_CACHE_BYTES / 8L);
    private static final Map<CacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicLong CACHE_CLOCK = new AtomicLong();
    private static final AtomicLong CACHE_BYTES = new AtomicLong();
    private static final Map<CacheKey, Future<HeightmapData>> PENDING = new ConcurrentHashMap<>();
    /** Single thread for pipeline.get() so MemoryTileStore is not accessed concurrently. */
    private static final ExecutorService INFERENCE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "terrain-diffusion-inference");
        t.setDaemon(true);
        return t;
    });

    private static volatile LocalTerrainProvider INSTANCE;
    private static long instanceSeed;

    private final WorldPipeline pipeline;

    private static final Object INIT_LOCK = new Object();

    private LocalTerrainProvider(long seed, PipelineModels models) {
        this.pipeline = new WorldPipeline(seed, models);
    }

    /** Seed is 64-bit world seed. Creates provider once; later worlds only update seed and clear caches (lightweight). */
    public static synchronized void init(long seed) {
        PipelineModels.awaitLoad();
        PipelineModels models = PipelineModels.getInstance();
        if (models == null) throw new IllegalStateException("PipelineModels failed to load");
        if (INSTANCE == null) {
            INSTANCE = new LocalTerrainProvider(seed, models);
            instanceSeed = seed;
        } else if (instanceSeed != seed) {
            INSTANCE.pipeline.setSeed(seed);
            instanceSeed = seed;
            clearCache();
        }
    }

    public static LocalTerrainProvider getInstance() {
        if (INSTANCE != null) return INSTANCE;

        synchronized(INIT_LOCK) {
            if (INSTANCE != null) return INSTANCE;
            PipelineModels.awaitLoad();
            PipelineModels models = PipelineModels.getInstance();
            if (models == null) throw new IllegalStateException("PipelineModels failed to load");
            INSTANCE = new LocalTerrainProvider(0L, models);
            instanceSeed = 0L;
        }

        return INSTANCE;
    }

    public static void clearCache() {
        CACHE.clear();
        PENDING.clear();
        CACHE_BYTES.set(0L);
    }

    /**
     * Non-blocking lookup for generation callbacks.
     *
     * <p>This intentionally does not start ML inference and does not wait on an
     * unfinished tile. Chunk lifecycle callbacks must not call fetchHeightmap()
     * because that method can block a chunk worker for a full model inference.
     */
    public static HeightmapData getReadyHeightmap(int i1, int j1, int i2, int j2) {
        CacheKey key = new CacheKey(i1, j1, i2, j2);

        CacheEntry cached = CACHE.get(key);
        if (cached != null) {
            cached.lastAccessed.set(CACHE_CLOCK.incrementAndGet());
            return cached.data;
        }

        Future<HeightmapData> pending = PENDING.get(key);
        if (pending == null || !pending.isDone()) {
            return null;
        }

        try {
            HeightmapData data = pending.get();
            if (data != null) {
                putCache(key, data);
            }
            return data;
        } catch (Exception ignored) {
            return null;
        }
    }

    // =========================================================================
    // Explorer API — all pipeline calls routed through INFERENCE_EXECUTOR
    // =========================================================================

    /** Returns the current world seed used by the pipeline. */
    public static long getSeed() {
        return instanceSeed;
    }

    /**
     * Run elevation and climate inference on the inference thread.
     *
     * @return float[2] : [0] = elev (H*W), [1] = climate (5*H*W, or null)
     */
    public static float[][] getPipelineData(int i1, int j1, int i2, int j2, boolean withClimate) throws Exception {
        return submitToInferenceThread(() -> getInstance().pipeline.get(i1, j1, i2, j2, withClimate));
    }

    /**
     * Fetch a coarse tensor slice on the inference thread.
     * Coordinates are in coarse index units (1 unit = 256 native pixels).
     *
     * @return FloatTensor with shape [7, ci1-ci0, cj1-cj0]
     */
    public static FloatTensor getPipelineCoarse(int ci0, int cj0, int ci1, int cj1) throws Exception {
        return submitToInferenceThread(() -> getInstance().pipeline.getCoarseSlice(ci0, cj0, ci1, cj1));
    }

    /**
     * Change the world seed used by the pipeline and clear all caches.
     * Note: this also affects terrain generation for new Minecraft chunks.
     */
    public static void changeSeedFromExplorer(long newSeed) throws Exception {
        submitToInferenceThread(() -> {
            LocalTerrainProvider provider = getInstance();
            provider.pipeline.setSeed(newSeed);
            instanceSeed = newSeed;
            clearCache();
            return null;
        });
    }

    /** Change to a random new seed; returns the new seed value. */
    public static long generateRandomSeedFromExplorer() throws Exception {
        long newSeed = new Random().nextLong();
        changeSeedFromExplorer(newSeed);
        return newSeed;
    }

    private static <T> T submitToInferenceThread(Callable<T> task) throws Exception {
        return INFERENCE_EXECUTOR.submit(task).get();
    }

    /**
     * Fetch heightmap for a block-coordinate region (i=Z, j=X).
     * Coordinates are in block space ; scale from config determines blocks per native pixel.
     * Blocks the calling thread until the tile is ready (one tile can take 10–30+ seconds).
     * If the caller is the server or a chunk worker the game will stall until this returns.
     */
    public HeightmapData fetchHeightmap(int i1, int j1, int i2, int j2) {
        CacheKey key = new CacheKey(i1, j1, i2, j2);
        CacheEntry cached = CACHE.get(key);
        if (cached != null) {
            cached.lastAccessed.set(CACHE_CLOCK.incrementAndGet());
            return cached.data;
        }

        return this.genHeightmap(key, i1, j1, i2, j2);
    }

    private HeightmapData genHeightmap(CacheKey key, int i1, int j1, int i2, int j2) {
        int scale = WorldScaleManager.getCurrentScale();
        FutureTask<HeightmapData> task = new FutureTask<>(() -> {
            long computedWindowCountBefore = pipeline.getTotalComputedWindowCount();
            HeightmapData data = scale <= 1
                    ? handle1x(i1, j1, i2, j2)
                    : handleUpsampled(i1, j1, i2, j2, scale);
            long computedWindowCountAfter = pipeline.getTotalComputedWindowCount();

            long newlyComputedWindowCount = computedWindowCountAfter - computedWindowCountBefore;
            int regionWidth = j2 - j1;
            int regionHeight = i2 - i1;
            LOG.info(
                    "Terrain Diffusion ({}) finished generating region {}x{} ({} newly computed windows)",
                    OnnxModel.getResolvedInferenceProvider(), regionWidth, regionHeight, newlyComputedWindowCount);
            putCache(key, data);
            PENDING.remove(key);
            return data;
        });
        Future<HeightmapData> existing = PENDING.putIfAbsent(key, task);
        FutureTask<HeightmapData> toRun = (existing == null) ? task : (FutureTask<HeightmapData>) existing;
        if (existing == null) {
            int regionWidth = j2 - j1;
            int regionHeight = i2 - i1;
            LOG.info(
                    "Terrain Diffusion ({}) uncached region requested: ({}, {})-({}, {}) size {}x{}",
                    OnnxModel.getResolvedInferenceProvider(), j1, i1, j2, i2, regionWidth, regionHeight);
            INFERENCE_EXECUTOR.submit(toRun);
        }
        try {
            return toRun.get();
        } catch (Exception e) {
            PENDING.remove(key);
            throw new RuntimeException("Terrain tile failed: " + key, e);
        }
    }

    private static void putCache(CacheKey key, HeightmapData data) {
        if (data == null) return;
        long bytes = data.estimatedBytes();
        CacheEntry entry = new CacheEntry(data, new AtomicLong(CACHE_CLOCK.incrementAndGet()), bytes);
        CacheEntry previous = CACHE.put(key, entry);
        long delta = bytes - (previous == null ? 0L : previous.bytes());
        CACHE_BYTES.addAndGet(delta);
        evictLruToBytes(MAX_CACHE_BYTES);
    }

    private static void evictLruToBytes(long maxBytes) {
        if (CACHE_BYTES.get() <= maxBytes) return;

        long targetBytes = Math.max(0L, maxBytes - CACHE_EVICTION_HEADROOM_BYTES);
        List<Map.Entry<CacheKey, CacheEntry>> entries = new ArrayList<>(CACHE.entrySet());
        entries.sort(Comparator.comparingLong(e -> e.getValue().lastAccessed.get()));

        for (Map.Entry<CacheKey, CacheEntry> entry : entries) {
            if (CACHE_BYTES.get() <= targetBytes) break;
            if (CACHE.remove(entry.getKey(), entry.getValue())) {
                CACHE_BYTES.addAndGet(-entry.getValue().bytes());
            }
        }
    }

    // =========================================================================
    // Scale == 1: block coords == native pixel coords
    // =========================================================================

    private HeightmapData handle1x(int i1, int j1, int i2, int j2) {
        int H = i2 - i1, W = j2 - j1;

        float[][] out = pipeline.get(i1, j1, i2, j2, true);
        float[] elevFlat = out[0];
        float[] climate  = out[1];

        int riverPad = MassFluxRiverCarver.ANALYSIS_PADDING_BLOCKS;
        int routingH = H + 2 * riverPad;
        int routingW = W + 2 * riverPad;
        float[] routingElev = pipeline.get(i1 - riverPad, j1 - riverPad, i2 + riverPad, j2 + riverPad, false)[0];
        float[] elevPadded = cropFlat(routingElev, routingH, routingW, riverPad - 1, riverPad - 1, H + 2, W + 2);
        MassFluxRiverCarver.Result river = MassFluxRiverCarver.carveTarget(
                routingElev, H + 2 * riverPad, W + 2 * riverPad, riverPad, riverPad, H, W, NATIVE_RESOLUTION);

        short[] biomeFlat = BiomeClassifier.classify(elevFlat, climate, i1, j1, elevPadded, H, W, NATIVE_RESOLUTION);
        MassFluxRiverCarver.applyRiverBiomes(biomeFlat, river.riverMask);
        return buildHeightmapData(river.elevation, biomeFlat, river.riverCells, H, W);
    }

    // =========================================================================
    // Scale > 1: pipeline at native res → bilinear upsample to block res
    // =========================================================================

    private HeightmapData handleUpsampled(int i1, int j1, int i2, int j2, int scale) {
        int H = i2 - i1, W = j2 - j1;
        float pixelSizeM = NATIVE_RESOLUTION / scale;

        // Convert block coords to native pixel coords
        int i1n = Math.floorDiv(i1, scale);
        int j1n = Math.floorDiv(j1, scale);
        int i2n = -Math.floorDiv(-i2, scale);
        int j2n = -Math.floorDiv(-j2, scale);

        // Native padding : 1 pixel for bilinear/slope plus a wider river-routing window.
        int riverPad = MassFluxRiverCarver.ANALYSIS_PADDING_BLOCKS;
        int nativePad = 2 + Math.max(1, (int) Math.ceil(riverPad / (float) scale));
        int i1p = i1n - nativePad, j1p = j1n - nativePad;
        int i2p = i2n + nativePad, j2p = j2n + nativePad;
        int nH = i2p - i1p, nW = j2p - j1p;

        float[][] out = pipeline.get(i1p, j1p, i2p, j2p, true);
        float[] elevNativeFlat    = out[0];
        float[] climateNativeFlat = out[1];

        // Bilinear upsample elevation : (nH, nW) -> (nH*scale, nW*scale)
        float[][] elevNative2D = to2D(elevNativeFlat, nH, nW);
        float[][] elevUp = LaplacianUtils.bilinearResize(elevNative2D, nH * scale, nW * scale);

        // Crop offsets in the upsampled array
        int padUp   = nativePad * scale;
        int offsetI = i1 - i1n * scale;
        int offsetJ = j1 - j1n * scale;
        int cropI1  = padUp + offsetI;
        int cropJ1  = padUp + offsetJ;

        float[] elevSmooth = cropFlat(elevUp, cropI1,     cropJ1,     H,   W,   nH * scale, nW * scale);
        float[] elevPadded = cropFlat(elevUp, cropI1 - 1, cropJ1 - 1, H+2, W+2, nH * scale, nW * scale);

        // Upsample climate (4, nH, nW) → (4, H, W)
        float[] climate = upsampleClimate(climateNativeFlat, nH, nW, cropI1, cropJ1, H, W, scale, nH * scale, nW * scale);

        float[] elevNoisy = addElevationNoise(elevSmooth, elevPadded, i1, j1, H, W, pixelSizeM);

        float[] riverRoutingElev = cropFlat(
                elevUp, cropI1 - riverPad, cropJ1 - riverPad, H + 2 * riverPad, W + 2 * riverPad,
                nH * scale, nW * scale);
        MassFluxRiverCarver.Result river = MassFluxRiverCarver.carveTarget(
                riverRoutingElev, H + 2 * riverPad, W + 2 * riverPad, riverPad, riverPad, H, W, pixelSizeM);

        float[] elevOut = elevNoisy.clone();
        for (int idx = 0; idx < elevOut.length; idx++) {
            elevOut[idx] = Math.min(elevOut[idx], river.elevation[idx]);
        }

        short[] biomeFlat = BiomeClassifier.classify(elevSmooth, climate, i1, j1, elevPadded, H, W, pixelSizeM);
        MassFluxRiverCarver.applyRiverBiomes(biomeFlat, river.riverMask);
        return buildHeightmapData(elevOut, biomeFlat, river.riverCells, H, W);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private float[] addElevationNoise(float[] elevSmooth, float[] elevPadded,
                                       int i1, int j1, int H, int W, float pixelSizeM) {
        float[] slopeGradient = sobelGradient(elevPadded, H + 2, W + 2, H, W);
        float[] elevOut = elevSmooth.clone();
        float normFactor = 40f * pixelSizeM / NATIVE_RESOLUTION;
        float ampC = 100f * pixelSizeM / NATIVE_RESOLUTION;
        float ampF = 70f  * pixelSizeM / NATIVE_RESOLUTION;

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float e = elevSmooth[idx];
                if (e < 0f) continue;

                float grad = slopeGradient[idx];
                float sf = Math.min(1f, grad / normFactor);
                sf = sf * sf * (float) Math.sqrt(sf);

                float nx = j1 + c, ny = i1 + r;
                elevOut[idx] = e
                        + ELEV_NOISE_COARSE.GetNoise(nx, ny) * ampC * sf
                        + ELEV_NOISE_FINE.GetNoise(nx, ny)   * ampF * sf;
            }
        }
        return elevOut;
    }

    private static float[] sobelGradient(float[] padded, int pH, int pW, int H, int W) {
        final float[] SOBEL_X = {-1,0,1, -2,0,2, -1,0,1};
        final float[] SOBEL_Y = {-1,-2,-1, 0,0,0, 1,2,1};
        float[] result = new float[H * W];
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                float dx = 0, dy = 0;
                for (int k = 0; k < 9; k++) {
                    float v = padded[(r + k/3) * pW + (c + k%3)];
                    dx += v * SOBEL_X[k];
                    dy += v * SOBEL_Y[k];
                }
                dx /= 8f; dy /= 8f;
                result[r * W + c] = (float) Math.sqrt(dx * dx + dy * dy);
            }
        }
        return result;
    }

    private static float[] upsampleClimate(float[] climNative, int nH, int nW,
                                            int cropI1, int cropJ1, int H, int W,
                                            int scale, int upH, int upW) {
        if (climNative == null) return null;
        float[] result = new float[4 * H * W];
        for (int ch = 0; ch < 4; ch++) {
            float[][] chNative = new float[nH][nW];
            for (int r = 0; r < nH; r++)
                System.arraycopy(climNative, ch * nH * nW + r * nW, chNative[r], 0, nW);
            float[][] chUp = LaplacianUtils.bilinearResize(chNative, upH, upW);
            for (int r = 0; r < H; r++)
                for (int c = 0; c < W; c++)
                    result[ch * H * W + r * W + c] = chUp[cropI1 + r][cropJ1 + c];
        }
        return result;
    }

    private static float[] cropFlat(float[] src, int srcH, int srcW, int r0, int c0, int H, int W) {
        float[] out = new float[H * W];
        for (int r = 0; r < H; r++) {
            int sr = Math.max(0, Math.min(srcH - 1, r0 + r));
            for (int c = 0; c < W; c++) {
                int sc = Math.max(0, Math.min(srcW - 1, c0 + c));
                out[r * W + c] = src[sr * srcW + sc];
            }
        }
        return out;
    }

    private static float[] cropFlat(float[][] src, int r0, int c0, int H, int W, int srcH, int srcW) {
        float[] out = new float[H * W];
        for (int r = 0; r < H; r++) {
            int sr = Math.max(0, Math.min(srcH - 1, r0 + r));
            for (int c = 0; c < W; c++)
                out[r * W + c] = src[sr][Math.max(0, Math.min(srcW - 1, c0 + c))];
        }
        return out;
    }

    private static float[][] to2D(float[] flat, int H, int W) {
        float[][] a = new float[H][W];
        for (int r = 0; r < H; r++) System.arraycopy(flat, r * W, a[r], 0, W);
        return a;
    }

    private static HeightmapData buildHeightmapData(float[] elevFlat, short[] biomeFlat, byte[] riverFlat, int H, int W) {
        int n = H * W;
        short[] heightmap = new short[n];
        short[] biomeIds  = new short[n];
        byte[] riverCells = riverFlat == null ? null : riverFlat.clone();
        for (int idx = 0; idx < n; idx++) {
            float e = elevFlat[idx];
            heightmap[idx] = (short) Math.max(-32768, Math.min(32767, (int) Math.floor(e)));
            biomeIds[idx]  = biomeFlat[idx];
        }
        return new HeightmapData(heightmap, biomeIds, riverCells, W, H);
    }
}
