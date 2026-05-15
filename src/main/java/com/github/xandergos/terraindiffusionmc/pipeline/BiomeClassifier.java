package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Data-driven biome classifier. Surface biomes are selected from regions defined in
 * biome-regions.json (loaded via {@link #compiledRegions}). Cave biomes remain hardcoded
 * via {@link #classifyCaveBiome}.
 */
public final class BiomeClassifier {

    // Variable indices for the data-driven region system
    public static final int VAR_TEMPERATURE   = 0;
    public static final int VAR_PRECIPITATION = 1;
    public static final int VAR_ELEVATION     = 2;
    public static final int VAR_SLOPE         = 3;
    public static final int VAR_COUNT         = 4;

    public static int varIndex(String name) {
        switch (name) {
            case "temperature":   return VAR_TEMPERATURE;
            case "precipitation": return VAR_PRECIPITATION;
            case "elevation":     return VAR_ELEVATION;
            case "slope":         return VAR_SLOPE;
            default:              return -1;
        }
    }

    /**
     * Compiled form of a biome region. Set by {@code BiomeRegionConfig.buildCompiled()} via
     * the BiomeSource constructor; read (without locking) in the classify hot loop.
     */
    public static final class CompiledRegion {
        // Per-variable normalization scale for distance computation
        private static final float[] DIST_SCALES = {60f, 2000f, 6000f, 1.5f};

        public final String name;
        public final float[] min;         // [VAR_COUNT] inclusive lower bounds
        public final float[] max;         // [VAR_COUNT] inclusive upper bounds
        public final short[] biomeIds;    // biome pool
        public final float[] priorities;  // per-biome priority (parallel to biomeIds)

        public CompiledRegion(String name, float[] min, float[] max, short[] biomeIds, float[] priorities) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.biomeIds = biomeIds;
            this.priorities = priorities;
        }

        public boolean matches(float[] vars) {
            for (int i = 0; i < VAR_COUNT; i++) {
                if (vars[i] < min[i] || vars[i] > max[i]) return false;
            }
            return true;
        }

        /** Normalized L1 distance from vars to the nearest point inside this region. */
        public float distanceTo(float[] vars) {
            float dist = 0f;
            for (int i = 0; i < VAR_COUNT; i++) {
                dist += Math.max(0f, min[i] - vars[i]) / DIST_SCALES[i];
                dist += Math.max(0f, vars[i] - max[i]) / DIST_SCALES[i];
            }
            return dist;
        }
    }

    /** Data-driven region array. Written once by the BiomeSource constructor; read-only thereafter. */
    public static volatile CompiledRegion[] compiledRegions = null;

    private static final FastNoiseLite TEMP_NOISE, TEMP_NOISE_FINE;
    private static final FastNoiseLite PRECIP_NOISE;
    private static final FastNoiseLite CAVE_BIOME_NOISE;
    private static final FastNoiseLite DEEP_DARK_NOISE;
    /**
     * Cellular/Voronoi noise and domain-warp noise for spatially-coherent biome patch selection.
     * Re-initialized by {@link #configureNoise} when biome-regions.json is loaded.
     * {@code WARP_NOISE} is null when domain warp is disabled (amplitude == 0).
     */
    private static volatile FastNoiseLite CELL_NOISE;
    private static volatile FastNoiseLite WARP_NOISE;
    // Per-thread mutable coordinate for DomainWarp; avoids allocation in the hot loop.
    private static final ThreadLocal<FastNoiseLite.Vector2> WARP_COORD =
            ThreadLocal.withInitial(() -> new FastNoiseLite.Vector2(0f, 0f));

    static {
        TEMP_NOISE = makeFnl(12345, 1f/500f, 3, 2f, 0.5f);
        TEMP_NOISE_FINE = makeFnl(54321, 1f/128f, 2, 2f, 0.5f);
        PRECIP_NOISE = makeFnl(12345, 1f/500f, 5, 2f, 0.5f);
        // Cave-biome region noise: smooth blob-shaped patches of lush vs dripstone.
        // Low frequency (~180-block scale) so a column has consistent cave biome through Y.
        CAVE_BIOME_NOISE = makeFnl(99991, 1f/180f, 3, 2f, 0.5f);
        // Deep dark: separate higher-octave noise for narrow, scattered patches.
        DEEP_DARK_NOISE = makeFnl(13577, 1f/250f, 4, 2f, 0.5f);

        configureNoise(300f, 200f, 80f, 2, 2f, 0.5f);
    }

    /**
     * (Re-)initialize the cellular and domain-warp noise instances.
     * Called once from the static block with defaults, then again from
     * {@code BiomeRegionConfig.load()} with user-configured values.
     *
     * @param cellScale      spatial scale of the Voronoi cells in pixels
     * @param warpScale      spatial scale of the domain-warp noise in pixels
     * @param warpAmp        maximum coordinate displacement; 0 disables warping
     * @param warpOctaves    fractal octave count for the domain warp
     * @param warpLacunarity frequency multiplier between warp octaves
     * @param warpGain       amplitude multiplier between warp octaves
     */
    public static void configureNoise(float cellScale, float warpScale, float warpAmp,
                                      int warpOctaves, float warpLacunarity, float warpGain) {
        FastNoiseLite cell = new FastNoiseLite(22222);
        cell.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        cell.SetFrequency(1f / Math.max(1f, cellScale));
        cell.SetCellularReturnType(FastNoiseLite.CellularReturnType.CellValue);
        CELL_NOISE = cell;

        if (warpAmp == 0f) {
            WARP_NOISE = null;
        } else {
            FastNoiseLite warp = new FastNoiseLite(33333);
            warp.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2);
            warp.SetFrequency(1f / Math.max(1f, warpScale));
            warp.SetDomainWarpAmp(warpAmp);
            warp.SetFractalType(FastNoiseLite.FractalType.DomainWarpProgressive);
            warp.SetFractalOctaves(Math.max(1, warpOctaves));
            warp.SetFractalLacunarity(warpLacunarity);
            warp.SetFractalGain(warpGain);
            WARP_NOISE = warp;
        }
    }

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

    static final short PLAINS = 1;
    static final short LUSH_CAVES = 60, DRIPSTONE_CAVES = 61, DEEP_DARK = 62;

    /**
     * Classify a cave biome at the given block-space coordinates, or return
     * {@code -1} if this position should keep its surface biome (i.e. is not
     * inside a cave-biome region).
     *
     * <p>Cave biomes only matter where a cave already exists — the label
     * controls decoration features (azaleas, dripstone clusters, sculk) and
     * any biome-keyed surface_rule branches. Above Y=30 we never override the
     * surface biome.
     *
     * <p>Region selection is driven by 2D (x,z) noise so a vertical column has
     * a consistent cave biome from top to bottom of its cave space, which
     * matches vanilla's blob-shaped cave-biome regions better than per-Y
     * randomness.
     */
    public static short classifyCaveBiome(int blockX, int blockY, int blockZ) {
        if (blockY > 30) return -1;

        // Per-thread (x, z) cache: chunk-gen iterates this method for every Y
        // quart in [-64, 30] = ~24 calls per column with identical (x, z). One
        // cache entry catches the trailing 23 to skip both noise samples.
        long key = ((long) blockX << 32) | (blockZ & 0xFFFFFFFFL);
        long[] kArr = CAVE_NOISE_KEY.get();
        float[] vArr = CAVE_NOISE_VALUES.get();
        float dd, cb;
        if (kArr[0] == key) {
            dd = vArr[0];
            cb = vArr[1];
        } else {
            dd = DEEP_DARK_NOISE.GetNoise((float) blockX, (float) blockZ);
            cb = CAVE_BIOME_NOISE.GetNoise((float) blockX, (float) blockZ);
            kArr[0] = key;
            vArr[0] = dd;
            vArr[1] = cb;
        }

        // Deep dark: rare patches restricted to low Y. Threshold tuned against
        // CaveBiomeNoiseTest output — DEEP_DARK_NOISE rarely exceeds 0.4, so
        // 0.28 gives roughly 5-8% of the area below Y=0.
        if (blockY < 0 && dd > 0.28f) return DEEP_DARK;

        // Lush vs dripstone: smooth blobs. CAVE_BIOME_NOISE concentrates near 0
        // so a ±0.10 split gives roughly 25% lush + 25% dripstone + 50% surface
        // biome (verified via CaveBiomeNoiseTest).
        if (cb >  0.10f) return LUSH_CAVES;
        if (cb < -0.10f) return DRIPSTONE_CAVES;

        return -1;
    }

    // Per-thread cache state for classifyCaveBiome. Static so all biome-source
    // instances share one cache per thread.
    private static final ThreadLocal<long[]> CAVE_NOISE_KEY =
            ThreadLocal.withInitial(() -> new long[]{Long.MIN_VALUE + 1});
    private static final ThreadLocal<float[]> CAVE_NOISE_VALUES =
            ThreadLocal.withInitial(() -> new float[2]);

    /**
     * Classify biomes for a grid of pixels.
     *
     * @param elev       elevation in meters, (H, W) row-major
     * @param climate    climate data (5, H, W) row-major or null
     * @param i0         top-left row in world space (for noise sampling)
     * @param j0         top-left col in world space
     * @param elevPadded elevation with 1-pixel padding, (H+2, W+2) row-major
     * @param H          height
     * @param W          width
     * @param pixelSizeM physical size of one pixel in meters
     * @return short array (H, W) with biome IDs
     */
    public static short[] classify(float[] elev, float[] climate, int i0, int j0,
                                    float[] elevPadded, int H, int W, float pixelSizeM) {
        if (climate == null || climate.length < 4 * H * W) {
            short[] out = new short[H * W];
            Arrays.fill(out, PLAINS);
            return out;
        }
        return classifyWithSlope(elev, climate, i0, j0, computeSlopeRatio(elevPadded, H, W, pixelSizeM), H, W);
    }

    /** Per-pixel climate and region debug info returned by {@link #debugClassify}. */
    public static final class DebugInfo {
        public final float temperature;
        public final float precipitation;
        public final float elevation;
        public final float slope;
        /** Names of all regions whose conditions matched the climate variables. */
        public final List<String> matchingRegions;
        /** Closest region used as fallback when no region matched; null otherwise. */
        public final String fallbackRegion;

        DebugInfo(float temperature, float precipitation, float elevation, float slope,
                  List<String> matchingRegions, String fallbackRegion) {
            this.temperature = temperature;
            this.precipitation = precipitation;
            this.elevation = elevation;
            this.slope = slope;
            this.matchingRegions = matchingRegions;
            this.fallbackRegion = fallbackRegion;
        }
    }

    /**
     * Compute debug classification info for a single native-resolution pixel.
     * Applies the same noise perturbations as the hot classify path.
     *
     * @param nx          column pixel coordinate (matches noise-sampling coords in classifyWithSlope)
     * @param ny          row pixel coordinate
     * @param elev3x3     elevation for a 3×3 region centred at (nx,ny), row-major, 9 elements;
     *                    returned by {@code getPipelineData(pz-1,px-1,pz+2,px+2)}
     * @param climate3x3  climate channels for the same 3×3 region, channel-major
     *                    ({@code climate[ch*9 + row*3 + col]}), at least 3×9 elements
     * @param pixelSizeM  physical size of one pixel in metres (= nativeResolution / scale)
     */
    public static DebugInfo debugClassify(float nx, float ny,
                                          float[] elev3x3, float[] climate3x3,
                                          float pixelSizeM) {
        final int CENTER = 4; // row=1, col=1 in the 3×3 grid

        float tempNoise    = 0.4f * TEMP_NOISE.GetNoise(nx, ny) + 0.2f * TEMP_NOISE_FINE.GetNoise(nx, ny);
        float precipFactor = 1f + 0.2f * PRECIP_NOISE.GetNoise(nx, ny);

        float temp   = climate3x3[CENTER] + tempNoise;                    // ch 0
        float precip = Math.max(0f, climate3x3[2 * 9 + CENTER]) * precipFactor; // ch 2
        float elev   = elev3x3[CENTER];
        float slope  = computeSlopeRatio(elev3x3, 1, 1, pixelSizeM)[0];

        float[] vars = new float[VAR_COUNT];
        vars[VAR_TEMPERATURE]   = temp;
        vars[VAR_PRECIPITATION] = precip;
        vars[VAR_ELEVATION]     = elev;
        vars[VAR_SLOPE]         = slope;

        CompiledRegion[] regions = compiledRegions;
        List<String> matching = new ArrayList<>();
        String fallback = null;

        if (regions != null) {
            for (CompiledRegion r : regions) {
                if (r.matches(vars)) matching.add(r.name);
            }
            if (matching.isEmpty()) {
                float bestDist = Float.MAX_VALUE;
                int bestIdx = -1;
                for (int i = 0; i < regions.length; i++) {
                    if (regions[i].biomeIds.length == 0) continue;
                    float d = regions[i].distanceTo(vars);
                    if (d < bestDist) { bestDist = d; bestIdx = i; }
                }
                if (bestIdx >= 0) {
                    fallback = String.format("%s (dist=%.3f)", regions[bestIdx].name, bestDist);
                }
            }
        }

        return new DebugInfo(temp, precip, elev, slope,
                Collections.unmodifiableList(matching), fallback);
    }

    /**
     * Classify biomes using a pre-computed slope-ratio array (avoids a redundant Sobel pass
     * when the caller already has the gradient for another purpose).
     *
     * @param slopeRatio per-pixel slope magnitude divided by pixelSizeM, (H,W) row-major
     */
    static short[] classifyWithSlope(float[] elev, float[] climate, int i0, int j0,
                                      float[] slopeRatio, int H, int W) {
        if (climate == null || climate.length < 4 * H * W) {
            short[] out = new short[H * W];
            Arrays.fill(out, PLAINS);
            return out;
        }
        short[] out = new short[H * W];

        CompiledRegion[] regions = compiledRegions; // read volatile once
        if (regions == null || regions.length == 0) {
            Arrays.fill(out, PLAINS);
            return out;
        }

        // Pool arrays allocated once per call, reused across pixels.
        final int MAX_POOL = 64;
        short[] poolBiomes  = new short[MAX_POOL];
        float[] poolPriSum  = new float[MAX_POOL];
        int[]   poolCount   = new int[MAX_POOL];
        float[] vars        = new float[VAR_COUNT];
        // Snapshot volatile noise refs once so the inner loop doesn't re-read them.
        FastNoiseLite cellNoise = CELL_NOISE;
        FastNoiseLite warpNoise = WARP_NOISE;

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float nx = j0 + c, ny = i0 + r;

                float tempNoiseVal       = 0.4f * TEMP_NOISE.GetNoise(nx, ny) + 0.2f * TEMP_NOISE_FINE.GetNoise(nx, ny);
                float precipNoiseFactVal = 1.0f + 0.2f * PRECIP_NOISE.GetNoise(nx, ny);

                vars[VAR_TEMPERATURE]   = climate[idx] + tempNoiseVal;
                vars[VAR_PRECIPITATION] = Math.max(0f, climate[2 * H * W + idx]) * precipNoiseFactVal;
                vars[VAR_ELEVATION]     = elev[idx];
                vars[VAR_SLOPE]         = slopeRatio[idx];

                int poolSize = 0;

                // Accumulate biomes from all matching regions; average priority for duplicates.
                for (CompiledRegion region : regions) {
                    if (!region.matches(vars)) continue;
                    for (int k = 0; k < region.biomeIds.length && poolSize < MAX_POOL; k++) {
                        short bid = region.biomeIds[k];
                        float pri = region.priorities[k];
                        int found = -1;
                        for (int p = 0; p < poolSize; p++) {
                            if (poolBiomes[p] == bid) { found = p; break; }
                        }
                        if (found >= 0) {
                            poolPriSum[found] += pri;
                            poolCount[found]++;
                        } else {
                            poolBiomes[poolSize] = bid;
                            poolPriSum[poolSize] = pri;
                            poolCount[poolSize]  = 1;
                            poolSize++;
                        }
                    }
                }

                // Fall back to closest region when no region matches.
                if (poolSize == 0) {
                    float bestDist = Float.MAX_VALUE;
                    int bestIdx = -1;
                    for (int ri = 0; ri < regions.length; ri++) {
                        if (regions[ri].biomeIds.length == 0) continue;
                        float d = regions[ri].distanceTo(vars);
                        if (d < bestDist) { bestDist = d; bestIdx = ri; }
                    }
                    if (bestIdx >= 0) {
                        CompiledRegion closest = regions[bestIdx];
                        for (int k = 0; k < closest.biomeIds.length && poolSize < MAX_POOL; k++) {
                            poolBiomes[poolSize] = closest.biomeIds[k];
                            poolPriSum[poolSize] = closest.priorities[k];
                            poolCount[poolSize]  = 1;
                            poolSize++;
                        }
                    }
                }

                short biome;
                if (poolSize == 0) {
                    biome = PLAINS;
                } else {
                    float total = 0f;
                    for (int p = 0; p < poolSize; p++) {
                        poolPriSum[p] /= poolCount[p];
                        total += poolPriSum[p];
                    }
                    float cx = nx, cy = ny;
                    if (warpNoise != null) {
                        FastNoiseLite.Vector2 coord = WARP_COORD.get();
                        coord.x = nx; coord.y = ny;
                        warpNoise.DomainWarp(coord);
                        cx = coord.x; cy = coord.y;
                    }
                    float cellVal = (cellNoise.GetNoise(cx, cy) + 1f) * 0.5f; // [0, 1]
                    float target  = cellVal * total;
                    float cumul   = 0f;
                    biome = poolBiomes[poolSize - 1];
                    for (int p = 0; p < poolSize; p++) {
                        cumul += poolPriSum[p];
                        if (cumul >= target) { biome = poolBiomes[p]; break; }
                    }
                }

                out[idx] = biome;
            }
        }
        return out;
    }

    private static float[] computeSlopeRatio(float[] elevPadded, int H, int W, float pixelSizeM) {
        // Sobel kernels / 8 applied to (H+2, W+2) padded array → (H, W) output
        float[] slope = new float[H * W];
        int PW = W + 2;
        float[] sx = {-1,0,1, -2,0,2, -1,0,1};
        float[] sy = {-1,-2,-1, 0,0,0, 1,2,1};
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                float dx = 0, dy = 0;
                for (int kr = 0; kr < 3; kr++)
                    for (int kc = 0; kc < 3; kc++) {
                        float v = elevPadded[(r + kr) * PW + (c + kc)];
                        dx += v * sx[kr * 3 + kc];
                        dy += v * sy[kr * 3 + kc];
                    }
                dx /= 8f; dy /= 8f;
                slope[r * W + c] = (float) Math.sqrt(dx * dx + dy * dy) / pixelSizeM;
            }
        }
        return slope;
    }
}
