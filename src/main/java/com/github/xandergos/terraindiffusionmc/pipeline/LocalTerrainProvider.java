package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

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
        public final short[][] heightmap;
        public final short[][] biomeIds;
        public final int width;
        public final int height;

        public HeightmapData(short[][] heightmap, short[][] biomeIds, int width, int height) {
            this.heightmap = heightmap;
            this.biomeIds  = biomeIds;
            this.width     = width;
            this.height    = height;
        }
    }

    private static final int MAX_CACHE_SIZE = 256;
    private static final ConcurrentHashMap<Long, HeightmapData> CACHE = new ConcurrentHashMap<>(256);
    private static final ConcurrentHashMap<Long, Future<HeightmapData>> PENDING = new ConcurrentHashMap<>();
    /**
     * Pool that runs pipeline tile inference. Sized via {@link #resolveInferenceThreadCount()}:
     * <ul>
     *   <li>CPU mode: 1 (ONNX Runtime already does intra-op parallelism on CPU; concurrent
     *       sessions oversubscribe cores).</li>
     *   <li>GPU + offload_models=true: 1 (concurrent threads would thrash the GPU slot lock,
     *       swapping models on every runModel call — strictly worse than serial).</li>
     *   <li>GPU + offload_models=false: respects {@code inference.threads} config (default 2).</li>
     * </ul>
     * <p>The win at pool>1 is mostly prefetch overlap: tile fetches no longer queue
     * sequentially behind the demanded tile, so the next tile is ready when a player
     * crosses a boundary. Raw inference throughput on a single GPU is bounded by SM
     * occupancy and is rarely 2× faster with two concurrent calls.
     */
    private static final ExecutorService INFERENCE_EXECUTOR = createInferenceExecutor();

    private static ExecutorService createInferenceExecutor() {
        int threadCount = resolveInferenceThreadCount();
        AtomicInteger threadId = new AtomicInteger(0);
        LOG.info("Terrain diffusion inference pool size: {}", threadCount);
        return Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "terrain-diffusion-inference-" + threadId.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Resolves the inference pool size, auto-clamping to 1 in modes where concurrency
     * would hurt rather than help. See {@link #INFERENCE_EXECUTOR} for rationale.
     */
    private static int resolveInferenceThreadCount() {
        int requested = TerrainDiffusionConfig.inferenceThreads();
        if (requested <= 1) return 1;
        String device = TerrainDiffusionConfig.inferenceDevice();
        if ("cpu".equals(device)) return 1;
        if (TerrainDiffusionConfig.offloadModels()) return 1;
        return requested;
    }

    private static volatile LocalTerrainProvider INSTANCE;
    private static long instanceSeed;

    private final WorldPipeline pipeline;

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
            CACHE.clear();
            PENDING.clear();
        }
    }

    public static LocalTerrainProvider getInstance() {
        // Fast path: volatile read avoids acquiring the class monitor on every density-function
        // evaluation (called by every chunk-generation thread, including C2ME worker threads).
        LocalTerrainProvider instance = INSTANCE;
        if (instance != null) return instance;
        synchronized (LocalTerrainProvider.class) {
            if (INSTANCE == null) {
                PipelineModels.awaitLoad();
                PipelineModels models = PipelineModels.getInstance();
                if (models == null) throw new IllegalStateException("PipelineModels failed to load");
                INSTANCE = new LocalTerrainProvider(0L, models);
                instanceSeed = 0L;
            }
            return INSTANCE;
        }
    }

    public static void clearCache() {
        CACHE.clear();
        PENDING.clear();
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
     * @return float[2]: [0] = elev (H*W), [1] = climate (5*H*W, or null)
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
            CACHE.clear();
            PENDING.clear();
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
     * Coordinates are in block space; scale from config determines blocks per native pixel.
     * Blocks the calling thread until the tile is ready (one tile can take 10–30+ seconds).
     * If the caller is the server or a chunk worker, the game will stall until this returns.
     */
    /** Pack (i1, j1) into a single long cache key. i2/j2 are always i1+tileSize / j1+tileSize. */
    private static long tileKey(int i1, int j1) {
        return ((long) i1 << 32) | (j1 & 0xFFFFFFFFL);
    }

    private static void putAndEvict(long key, HeightmapData data) {
        CACHE.put(key, data);
        int excess = CACHE.size() - MAX_CACHE_SIZE;
        if (excess > 0) {
            Iterator<Long> it = CACHE.keySet().iterator();
            for (int i = 0; i < excess && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
        }
    }

    public HeightmapData fetchHeightmap(int i1, int j1, int i2, int j2) {
        long key = tileKey(i1, j1);
        HeightmapData cached = CACHE.get(key);
        if (cached != null) return cached;

        int scale = WorldScaleManager.getCurrentScale();
        FutureTask<HeightmapData> task = new FutureTask<>(() -> {
            long computedWindowCountBefore = pipeline.getTotalComputedWindowCount();
            HeightmapData data = scale <= 1
                    ? handle1x(i1, j1, i2, j2)
                    : handleUpsampled(i1, j1, i2, j2, scale);
            long newlyComputedWindowCount = pipeline.getTotalComputedWindowCount() - computedWindowCountBefore;
            LOG.info(
                    "Terrain Diffusion ({}) finished generating region {}x{} ({} newly computed windows)",
                    OnnxModel.getResolvedInferenceProvider(), j2 - j1, i2 - i1, newlyComputedWindowCount);
            putAndEvict(key, data);
            PENDING.remove(key);
            return data;
        });
        Future<HeightmapData> existing = PENDING.putIfAbsent(key, task);
        FutureTask<HeightmapData> toRun = (existing == null) ? task : (FutureTask<HeightmapData>) existing;
        if (existing == null) {
            LOG.info(
                    "Terrain Diffusion ({}) uncached region requested: ({}, {})-({}, {}) size {}x{}",
                    OnnxModel.getResolvedInferenceProvider(), j1, i1, j2, i2, j2 - j1, i2 - i1);
            INFERENCE_EXECUTOR.submit(toRun);

            // Speculatively warm the four adjacent tiles while this one generates.
            // Guard: only prefetch when the queue is nearly empty so bulk LOD requests
            // from mods like Voxy don't cascade into exponential prefetch storms.
            if (PENDING.size() <= 2) {
                int tH = i2 - i1, tW = j2 - j1;
                prefetchIfAbsent(i1 - tH, j1, i2 - tH, j2, scale);
                prefetchIfAbsent(i1 + tH, j1, i2 + tH, j2, scale);
                prefetchIfAbsent(i1, j1 - tW, i2, j2 - tW, scale);
                prefetchIfAbsent(i1, j1 + tW, i2, j2 + tW, scale);
            }
        }
        try {
            return toRun.get();
        } catch (Exception e) {
            PENDING.remove(key);
            throw new RuntimeException("Terrain tile failed: " + key, e);
        }
    }

    /**
     * Submit a tile for background generation without blocking the caller.
     * No-ops if the tile is already cached or in flight.
     * The resulting Future is stored in PENDING so any later fetchHeightmap() call for the
     * same region attaches to it rather than spawning a duplicate inference run.
     */
    private void prefetchIfAbsent(int i1, int j1, int i2, int j2, int scale) {
        long key = tileKey(i1, j1);
        if (CACHE.containsKey(key) || PENDING.containsKey(key)) return;
        FutureTask<HeightmapData> task = new FutureTask<>(() -> {
            HeightmapData data = scale <= 1
                    ? handle1x(i1, j1, i2, j2)
                    : handleUpsampled(i1, j1, i2, j2, scale);
            putAndEvict(key, data);
            PENDING.remove(key);
            return data;
        });
        if (PENDING.putIfAbsent(key, task) == null) {
            INFERENCE_EXECUTOR.submit(task);
        }
    }

    // =========================================================================
    // Scale == 1: block coords == native pixel coords
    // =========================================================================

    private HeightmapData handle1x(int i1, int j1, int i2, int j2) {
        int H = i2 - i1, W = j2 - j1;

        // Two pipeline calls — matches upstream behaviour. The previous single-call
        // refactor (efficient: read padded once and crop) produced subtly different
        // per-pixel elev values at shorelines because the pipeline's Laplacian-decode
        // step depends on the requested region's padding context. Using two separate
        // calls (one padded, one un-padded) restores upstream-identical elev values
        // for the inner region, which is what gets fed to the density function.
        float[] elevPadded = pipeline.get(i1 - 1, j1 - 1, i2 + 1, j2 + 1, false)[0];
        float[][] out = pipeline.get(i1, j1, i2, j2, true);
        float[] elevFlat = out[0];
        float[] climate  = out[1];

        short[] biomeFlat = BiomeClassifier.classify(elevFlat, climate, i1, j1, elevPadded, H, W, NATIVE_RESOLUTION);
        return buildHeightmapData(elevFlat, biomeFlat, H, W);
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

        // 2-pixel native padding (1 for bilinear + 1 for slope)
        int i1p = i1n - 2, j1p = j1n - 2;
        int i2p = i2n + 2, j2p = j2n + 2;
        int nH = i2p - i1p, nW = j2p - j1p;

        float[][] out = pipeline.get(i1p, j1p, i2p, j2p, true);
        float[] elevNativeFlat    = out[0];
        float[] climateNativeFlat = out[1];

        // Bilinear upsample elevation: (nH, nW) → (nH*scale, nW*scale)
        float[][] elevNative2D = to2D(elevNativeFlat, nH, nW);
        float[][] elevUp = LaplacianUtils.bilinearResize(elevNative2D, nH * scale, nW * scale);

        // Crop offsets in the upsampled array
        int padUp   = 2 * scale;
        int offsetI = i1 - i1n * scale;
        int offsetJ = j1 - j1n * scale;
        int cropI1  = padUp + offsetI;
        int cropJ1  = padUp + offsetJ;

        float[] elevSmooth = cropFlat(elevUp, cropI1,     cropJ1,     H,   W,   nH * scale, nW * scale);
        float[] elevPadded = cropFlat(elevUp, cropI1 - 1, cropJ1 - 1, H+2, W+2, nH * scale, nW * scale);

        // Upsample climate (4, nH, nW) → (4, H, W)
        float[] climate = upsampleClimate(climateNativeFlat, nH, nW, cropI1, cropJ1, H, W, scale, nH * scale, nW * scale);

        // Compute Sobel gradient once; share between noise blending and biome classification.
        float[] slopeGradient = sobelGradient(elevPadded, H + 2, W + 2, H, W);
        float[] elevOut = addElevationNoise(elevSmooth, slopeGradient, i1, j1, H, W, pixelSizeM);

        float[] slopeRatio = new float[H * W];
        for (int k = 0; k < H * W; k++) slopeRatio[k] = slopeGradient[k] / pixelSizeM;
        short[] biomeFlat = BiomeClassifier.classifyWithSlope(elevSmooth, climate, i1, j1, slopeRatio, H, W);
        return buildHeightmapData(elevOut, biomeFlat, H, W);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private float[] addElevationNoise(float[] elevSmooth, float[] slopeGradient,
                                       int i1, int j1, int H, int W, float pixelSizeM) {
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

    private static HeightmapData buildHeightmapData(float[] elevFlat, short[] biomeFlat, int H, int W) {
        short[][] heightmap = new short[H][W];
        short[][] biomeIds  = new short[H][W];
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                float e = elevFlat[r * W + c];
                heightmap[r][c] = (short) Math.max(-32768, Math.min(32767, (int) Math.floor(e)));
                biomeIds[r][c]  = biomeFlat[r * W + c];
            }
        }
        return new HeightmapData(heightmap, biomeIds, W, H);
    }
}
