package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Rule-based biome classifier port of _classify_biome in minecraft_api.py.
 *
 * <p>Uses fixed-seed FastNoiseLite instances for climate and elevation noise perturbations.
 * Biome IDs match the Python server's _BIOME_ID mapping.
 *
 * <p>When {@link #compiledRegions} is non-null (set by the biome source after loading
 * biome-regions.json), surface classification is fully data-driven. Cave biomes remain
 * hardcoded via {@link #classifyCaveBiome}.
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

        public final float[] min;         // [VAR_COUNT] inclusive lower bounds
        public final float[] max;         // [VAR_COUNT] inclusive upper bounds
        public final short[] biomeIds;    // biome pool
        public final float[] priorities;  // per-biome priority (parallel to biomeIds)

        public CompiledRegion(float[] min, float[] max, short[] biomeIds, float[] priorities) {
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

    /**
     * Data-driven region array. Null means use the hardcoded classifier.
     * Written once by the BiomeSource constructor; read-only thereafter.
     */
    public static volatile CompiledRegion[] compiledRegions = null;

    // Fixed-seed noise instances (matching Python's module-level _TEMP_NOISE etc.)
    private static final FastNoiseLite TEMP_NOISE, TEMP_NOISE_FINE;
    private static final FastNoiseLite PRECIP_NOISE;
    private static final FastNoiseLite SNOW_NOISE, SNOW_NOISE_FINE;
    private static final FastNoiseLite BIOME_VARIANT_NOISE;
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
        SNOW_NOISE = makeFnl(12345, 1f/500f, 3, 2f, 0.5f);
        SNOW_NOISE_FINE = makeFnl(54321, 1f/128f, 2, 2f, 0.5f);
        BIOME_VARIANT_NOISE = makeFnl(77777, 1f/300f, 3, 2f, 0.5f);
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

    // Vanilla biome IDs
    static final short PLAINS = 1, SNOWY_PLAINS = 3, DESERT = 5, SWAMP = 6;
    static final short FOREST = 8, TAIGA = 15, SNOWY_TAIGA = 16, SAVANNA = 17;
    static final short WINDSWEPT_HILLS = 19, JUNGLE = 23, BADLANDS = 26, MEADOW = 29;
    static final short GROVE = 31, SNOWY_SLOPES = 32, FROZEN_PEAKS = 33, STONY_PEAKS = 35;
    static final short WARM_OCEAN = 41, OCEAN = 44, COLD_OCEAN = 46, FROZEN_OCEAN = 48;
    static final short FOREST_SPARSE = 108, TAIGA_SPARSE = 115, SNOWY_TAIGA_SPARSE = 116;

    // Oh The Biomes We've Gone biome IDs (200–254)
    static final short BWG_ALLIUM_SHRUBLAND = 200, BWG_AMARANTH_GRASSLAND = 201;
    static final short BWG_ARAUCARIA_SAVANNA = 202, BWG_ASPEN_BOREAL = 203;
    static final short BWG_ATACAMA_OUTBACK = 204, BWG_BAOBAB_SAVANNA = 205;
    static final short BWG_BASALT_BARRERA = 206, BWG_BAYOU = 207;
    static final short BWG_BLACK_FOREST = 208, BWG_CANADIAN_SHIELD = 209;
    static final short BWG_CIKA_WOODS = 210, BWG_COCONINO_MEADOW = 211;
    static final short BWG_CONIFEROUS_FOREST = 212, BWG_CRAG_GARDENS = 213;
    static final short BWG_CRIMSON_TUNDRA = 214, BWG_CYPRESS_SWAMPLANDS = 215;
    static final short BWG_CYPRESS_WETLANDS = 216, BWG_DACITE_RIDGES = 217;
    static final short BWG_DACITE_SHORE = 218, BWG_DEAD_SEA = 219;
    static final short BWG_EBONY_WOODS = 220, BWG_ENCHANTED_TANGLE = 221;
    static final short BWG_ERODED_BOREALIS = 222, BWG_FIRECRACKER_CHAPARRAL = 223;
    static final short BWG_FORGOTTEN_FOREST = 224, BWG_FRAGMENT_JUNGLE = 225;
    static final short BWG_FROSTED_CONIFEROUS_FOREST = 226, BWG_FROSTED_TAIGA = 227;
    static final short BWG_HOWLING_PEAKS = 228, BWG_IRONWOOD_GOUR = 229;
    static final short BWG_JACARANDA_JUNGLE = 230, BWG_LUSH_STACKS = 231;
    static final short BWG_MAPLE_TAIGA = 232, BWG_MOJAVE_DESERT = 233;
    static final short BWG_ORCHARD = 234, BWG_OVERGROWTH_WOODLANDS = 235;
    static final short BWG_PALE_BOG = 236, BWG_PRAIRIE = 237;
    static final short BWG_PUMPKIN_VALLEY = 238, BWG_RAINBOW_BEACH = 239;
    static final short BWG_RED_ROCK_VALLEY = 240, BWG_RED_ROCK_PEAKS = 241;
    static final short BWG_REDWOOD_THICKET = 242, BWG_ROSE_FIELDS = 243;
    static final short BWG_RUGGED_BADLANDS = 244, BWG_SAKURA_GROVE = 245;
    static final short BWG_SHATTERED_GLACIER = 246, BWG_SIERRA_BADLANDS = 247;
    static final short BWG_SKYRIS_VALE = 248, BWG_TROPICAL_RAINFOREST = 249;
    static final short BWG_TEMPERATE_GROVE = 250, BWG_WEEPING_WITCH_FOREST = 251;
    static final short BWG_WHITE_MANGROVE_MARSHES = 252, BWG_WINDSWEPT_DESERT = 253;
    static final short BWG_ZELKOVA_FOREST = 254;

    // Cave biomes
    static final short LUSH_CAVES = 60;
    static final short DRIPSTONE_CAVES = 61;
    static final short DEEP_DARK = 62;

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
            for (int i = 0; i < H * W; i++) out[i] = PLAINS;
            return out;
        }
        return classifyWithSlope(elev, climate, i0, j0, computeSlopeRatio(elevPadded, H, W, pixelSizeM), H, W);
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
            for (int i = 0; i < H * W; i++) out[i] = PLAINS;
            return out;
        }
        short[] out = new short[H * W];

        // Allocate pool arrays once per call for data-driven region selection.
        // Reused across all pixels by resetting poolSize to 0 at the start of each pixel.
        final int MAX_POOL = 64;
        CompiledRegion[] regions = compiledRegions; // read volatile once
        short[] poolBiomes      = null;
        float[] poolPriSum      = null;
        int[]   poolCount       = null;
        float[] vars            = null;
        FastNoiseLite cellNoise = null;
        FastNoiseLite warpNoise = null;
        if (regions != null && regions.length > 0) {
            poolBiomes = new short[MAX_POOL];
            poolPriSum = new float[MAX_POOL];
            poolCount  = new int[MAX_POOL];
            vars       = new float[VAR_COUNT];
            // Snapshot volatile noise refs once so the inner loop doesn't re-read them.
            cellNoise  = CELL_NOISE;
            warpNoise  = WARP_NOISE;
        }

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float nx = j0 + c, ny = i0 + r;

                // Inline noise (eliminates separate noise-pass + 3 temporary arrays)
                float tnc = TEMP_NOISE.GetNoise(nx, ny);
                float tnf = TEMP_NOISE_FINE.GetNoise(nx, ny);
                float tempNoiseVal = 0.4f * tnc + 0.2f * tnf;
                float precipNoiseFactVal = 1.0f + 0.2f * PRECIP_NOISE.GetNoise(nx, ny);
                float snc = SNOW_NOISE.GetNoise(nx, ny);
                float snf = SNOW_NOISE_FINE.GetNoise(nx, ny);
                float snowNoiseVal = 3.0f * snc + 2.0f * snf;

                float elevVal = elev[idx];
                float altM    = Math.max(0f, elevVal);
                float slope   = slopeRatio[idx];

                // Climate channels: [0]=temp, [1]=t_season, [2]=precip, [3]=p_cv
                float temp    = climate[idx] + tempNoiseVal;
                float tSeason = climate[H * W + idx];
                float precip  = Math.max(0f, climate[2 * H * W + idx]) * precipNoiseFactVal;
                float pCV     = climate[3 * H * W + idx];

                short biome;

                // ── Data-driven path ─────────────────────────────────────────────────
                if (vars != null) {
                    vars[VAR_TEMPERATURE]   = temp;
                    vars[VAR_PRECIPITATION] = precip;
                    vars[VAR_ELEVATION]     = elevVal;
                    vars[VAR_SLOPE]         = slope;

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

                    if (poolSize == 0) {
                        biome = PLAINS;
                    } else {
                        // Average priorities for duplicates, then compute total weight.
                        float total = 0f;
                        for (int p = 0; p < poolSize; p++) {
                            poolPriSum[p] /= poolCount[p];
                            total += poolPriSum[p];
                        }
                        // Domain-warp the lookup coordinates before sampling cell noise.
                        // Uses a per-thread reusable Vector2 to avoid allocation.
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
                        biome = poolBiomes[poolSize - 1]; // default to last entry
                        for (int p = 0; p < poolSize; p++) {
                            cumul += poolPriSum[p];
                            if (cumul >= target) { biome = poolBiomes[p]; break; }
                        }
                    }

                    out[idx] = biome;
                    continue;
                }

                // ── Hardcoded fallback path (used when no biome-regions.json is loaded) ──

                // Derived climate variables
                float tStd     = tSeason / 100f;
                float tEff     = Math.max(0f, temp + 0.5f * tStd);
                float pet      = Math.max(250f, 250f + 25f * tEff + 0.7f * tEff * tEff);
                float aridity  = precip / Math.max(1f, pet);
                float seasonPenalty = 1f - 0.35f * Math.min(1f, pCV / 100f);
                float treeMoisture = aridity * seasonPenalty;

                // Growing season
                float amplitude = tStd * 1.414f;
                float growingSeason;
                if (amplitude < 0.1f) {
                    growingSeason = temp > 5f ? 365f : 0f;
                } else {
                    float x = (5f - temp) / amplitude;
                    if (x <= -1f) growingSeason = 365f;
                    else if (x >= 1f) growingSeason = 0f;
                    else growingSeason = 365f * (0.5f - (float) Math.asin(Math.max(-1f, Math.min(1f, x))) / (float) Math.PI);
                }

                float gsFactor = Math.max(0f, Math.min(1f, (growingSeason - 60f) / (150f - 60f)));
                float effTreeMoisture = treeMoisture * gsFactor;

                // Slope-dependent bare threshold
                float moistureFactor = Math.max(0f, Math.min(1f, (treeMoisture - 0.35f) / 0.45f));
                float bareThreshold = 0.7f + (1.19f - 0.7f) * moistureFactor;

                // Tree coverage classification
                boolean treesNone = effTreeMoisture < 0.2f;
                boolean tooArid   = treeMoisture < 0.05f;
                boolean tooCold   = growingSeason < 60f;
                boolean barren    = tooArid || tooCold;
                boolean treesSparse    = !treesNone && effTreeMoisture < 0.5f;
                boolean treesForest    = !treesNone && effTreeMoisture >= 0.5f && effTreeMoisture < 0.8f;
                boolean treesDense     = !treesNone && effTreeMoisture >= 0.8f && effTreeMoisture < 1.3f;
                boolean treesRainforest = !treesNone && effTreeMoisture >= 1.3f;

                // Slope overrides
                boolean slopeMedium = slope >= 0.62f && slope < bareThreshold;
                boolean slopeBare   = slope >= bareThreshold;
                if (slopeMedium) {
                    if (treesForest || treesDense || treesRainforest) { treesSparse = true; }
                    treesForest = treesForest && false; treesDense = false; treesRainforest = false;
                }
                if (slopeBare) {
                    treesNone = true; treesSparse = false; treesForest = false;
                    treesDense = false; treesRainforest = false;
                }

                // Snow classification
                float snowTemp = temp + snowNoiseVal;
                boolean isSteep = slope > 0.78f;
                boolean hasSnow = snowTemp < 0f && precip > 150f && !isSteep;

                // Elevation/temp bands
                boolean isOcean   = elevVal < 0f;
                boolean mountains = altM > 2500f;
                boolean lowland   = altM < 200f;
                boolean frozen    = temp < -5f;
                boolean cold      = temp >= -5f && temp < 5f;
                boolean cool      = temp >= 5f  && temp < 12f;
                boolean temperate = temp >= 12f && temp < 20f;
                boolean warm      = temp >= 20f && temp < 26f;
                boolean hot       = temp >= 26f;

                biome = PLAINS;

                if (isOcean) {
                    if (frozen) biome = FROZEN_OCEAN;
                    else if (cold) biome = COLD_OCEAN;
                    else if (warm || hot) biome = WARM_OCEAN;
                    else biome = OCEAN;
                } else if (mountains) {
                    if (slopeBare) {
                        biome = hasSnow ? FROZEN_PEAKS : STONY_PEAKS;
                    } else if (hasSnow) {
                        if (treesNone) biome = SNOWY_SLOPES;
                        else if (treesSparse || treesForest) biome = SNOWY_TAIGA_SPARSE;
                        else biome = SNOWY_TAIGA;
                    } else if (treesNone) {
                        if (barren) biome = WINDSWEPT_HILLS;
                        else if (treeMoisture < 0.35f || precip < 350f) biome = GROVE;
                        else biome = PLAINS;
                    } else if (treesSparse || treesForest) {
                        biome = TAIGA_SPARSE;
                    } else {
                        biome = TAIGA;
                    }
                } else {
                    // Lowland/midland
                    if (hasSnow && treesNone) {
                        biome = SNOWY_PLAINS;
                    } else if (hasSnow) {
                        biome = (treesSparse || treesForest) ? SNOWY_TAIGA_SPARSE : SNOWY_TAIGA;
                    } else if (treesNone) {
                        if (warm || hot) biome = DESERT;
                        else if (barren && !lowland && (cold || cool || temperate)) biome = GROVE;
                        else if (treeMoisture < 0.35f || precip < 350f) biome = GROVE;
                        else biome = PLAINS;
                    } else if (treesSparse || treesForest) {
                        if (hot) biome = JUNGLE;
                        else if (warm && treesSparse && !slopeMedium) biome = SAVANNA;
                        else if (warm && treesForest) biome = FOREST_SPARSE;
                        else if (temperate) biome = FOREST_SPARSE;
                        else biome = TAIGA_SPARSE;
                    } else if (treesDense) {
                        if (hot) biome = JUNGLE;
                        else if (warm && lowland) biome = SWAMP;
                        else if (cool || cold) biome = TAIGA;
                        else biome = FOREST;
                    } else { // rainforest
                        if (hot || (warm && temp >= 18f && tStd < 5f)) biome = JUNGLE;
                        else if (lowland) biome = SWAMP;
                        else if (cool || cold) biome = TAIGA;
                        else biome = FOREST;
                    }
                }

                // Bare slope override for lowland/non-mountain cliffs
                if (slopeBare && !isOcean && !mountains) {
                    biome = hasSnow ? FROZEN_PEAKS : STONY_PEAKS;
                }

                // BWG biome variant selection – spatially-coherent override using low-frequency noise
                float vn = BIOME_VARIANT_NOISE.GetNoise(nx, ny); // [-1, 1]
                if (biome == PLAINS) {
                    if      (vn < -0.667f) biome = BWG_ALLIUM_SHRUBLAND;
                    else if (vn < -0.333f) biome = BWG_ROSE_FIELDS;
                    else if (vn <  0.0f  ) biome = BWG_COCONINO_MEADOW;
                    else if (vn <  0.333f) biome = BWG_PUMPKIN_VALLEY;
                    else if (vn <  0.667f) biome = BWG_SKYRIS_VALE;
                } else if (biome == FOREST) {
                    if      (vn < -0.8f  ) biome = BWG_BLACK_FOREST;
                    else if (vn < -0.6f  ) biome = BWG_EBONY_WOODS;
                    else if (vn < -0.4f  ) biome = BWG_FORGOTTEN_FOREST;
                    else if (vn < -0.2f  ) biome = BWG_SAKURA_GROVE;
                    else if (vn <  0.0f  ) biome = BWG_WEEPING_WITCH_FOREST;
                    else if (vn <  0.2f  ) biome = BWG_ZELKOVA_FOREST;
                    else if (vn <  0.4f  ) biome = BWG_OVERGROWTH_WOODLANDS;
                    else if (vn <  0.6f  ) biome = BWG_CIKA_WOODS;
                    else if (vn <  0.8f  ) biome = BWG_LUSH_STACKS;
                } else if (biome == FOREST_SPARSE) {
                    if      (vn < -0.333f) biome = BWG_ORCHARD;
                    else if (vn <  0.333f) biome = BWG_TEMPERATE_GROVE;
                } else if (biome == TAIGA) {
                    if      (vn < -0.6f  ) biome = BWG_CONIFEROUS_FOREST;
                    else if (vn < -0.2f  ) biome = BWG_MAPLE_TAIGA;
                    else if (vn <  0.2f  ) biome = BWG_REDWOOD_THICKET;
                    else if (vn <  0.6f  ) biome = BWG_ASPEN_BOREAL;
                } else if (biome == TAIGA_SPARSE) {
                    if      (vn < -0.333f) biome = BWG_CONIFEROUS_FOREST;
                    else if (vn <  0.333f) biome = BWG_MAPLE_TAIGA;
                } else if (biome == SNOWY_PLAINS) {
                    if (vn < 0.0f) biome = BWG_CRIMSON_TUNDRA;
                } else if (biome == SNOWY_TAIGA) {
                    if      (vn < -0.333f) biome = BWG_FROSTED_CONIFEROUS_FOREST;
                    else if (vn <  0.333f) biome = BWG_ASPEN_BOREAL;
                } else if (biome == SNOWY_TAIGA_SPARSE) {
                    if (vn < 0.0f) biome = BWG_FROSTED_TAIGA;
                } else if (biome == DESERT) {
                    if (hot) {
                        if      (vn < -0.5f  ) biome = BWG_ATACAMA_OUTBACK;
                        else if (vn <  0.0f  ) biome = BWG_MOJAVE_DESERT;
                        else if (vn <  0.5f  ) biome = BWG_WINDSWEPT_DESERT;
                    } else { // warm
                        if      (vn < -0.667f) biome = BWG_AMARANTH_GRASSLAND;
                        else if (vn < -0.333f) biome = BWG_PRAIRIE;
                        else if (vn <  0.0f  ) biome = BWG_RED_ROCK_VALLEY;
                        else if (vn <  0.333f) biome = BWG_RUGGED_BADLANDS;
                        else if (vn <  0.667f) biome = BWG_SIERRA_BADLANDS;
                    }
                } else if (biome == SWAMP) {
                    if (warm) {
                        if      (vn < -0.5f  ) biome = BWG_BAYOU;
                        else if (vn <  0.0f  ) biome = BWG_CYPRESS_SWAMPLANDS;
                        else if (vn <  0.5f  ) biome = BWG_CYPRESS_WETLANDS;
                        else if (vn <  0.75f ) biome = BWG_WHITE_MANGROVE_MARSHES;
                    } else {
                        if (vn < 0.0f) biome = BWG_PALE_BOG;
                    }
                } else if (biome == SAVANNA) {
                    if      (vn < -0.6f  ) biome = BWG_ARAUCARIA_SAVANNA;
                    else if (vn < -0.2f  ) biome = BWG_BAOBAB_SAVANNA;
                    else if (vn <  0.2f  ) biome = BWG_IRONWOOD_GOUR;
                    else if (vn <  0.6f  ) biome = BWG_FIRECRACKER_CHAPARRAL;
                } else if (biome == JUNGLE) {
                    if      (vn < -0.667f) biome = BWG_JACARANDA_JUNGLE;
                    else if (vn < -0.333f) biome = BWG_TROPICAL_RAINFOREST;
                    else if (vn <  0.0f  ) biome = BWG_ENCHANTED_TANGLE;
                    else if (vn <  0.333f) biome = BWG_FRAGMENT_JUNGLE;
                    else if (vn <  0.667f) biome = BWG_CRAG_GARDENS;
                } else if (biome == GROVE) {
                    if      (vn < -0.333f) biome = BWG_CANADIAN_SHIELD;
                    else if (vn <  -0.28f) biome = BWG_ERODED_BOREALIS; // ~2.5% of grove tiles
                } else if (biome == WINDSWEPT_HILLS) {
                    if (vn < 0.0f) biome = BWG_BASALT_BARRERA;
                } else if (biome == STONY_PEAKS) {
                    if      (vn < -0.5f  ) biome = BWG_RED_ROCK_PEAKS;
                    else if (vn <  0.0f  ) biome = BWG_DACITE_RIDGES;
                    else if (vn <  0.5f  ) biome = BWG_HOWLING_PEAKS;
                } else if (biome == FROZEN_PEAKS) {
                    if (vn < 0.0f) biome = BWG_SHATTERED_GLACIER;
                } else if (biome == WARM_OCEAN) {
                    if      (vn < -0.5f  ) biome = BWG_RAINBOW_BEACH;
                    else if (vn <  0.45f ) biome = BWG_DACITE_SHORE;
                    else if (vn <  0.5f  ) biome = BWG_DEAD_SEA; // ~2.5% of warm ocean tiles
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
