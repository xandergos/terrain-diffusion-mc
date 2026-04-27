package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Rule-based biome classifier port of _classify_biome in minecraft_api.py.
 *
 * <p>Uses fixed-seed FastNoiseLite instances for climate and elevation noise perturbations.
 * Biome IDs match the Python server's _BIOME_ID mapping.
 */
public final class BiomeClassifier {

    // Fixed-seed noise instances (matching Python's module-level _TEMP_NOISE etc.)
    private static final FastNoiseLite TEMP_NOISE, TEMP_NOISE_FINE;
    private static final FastNoiseLite PRECIP_NOISE;
    private static final FastNoiseLite SNOW_NOISE, SNOW_NOISE_FINE;
    private static final FastNoiseLite BIOME_VARIANT_NOISE;

    static {
        TEMP_NOISE = makeFnl(12345, 1f/500f, 3, 2f, 0.5f);
        TEMP_NOISE_FINE = makeFnl(54321, 1f/128f, 2, 2f, 0.5f);
        PRECIP_NOISE = makeFnl(12345, 1f/500f, 5, 2f, 0.5f);
        SNOW_NOISE = makeFnl(12345, 1f/500f, 3, 2f, 0.5f);
        SNOW_NOISE_FINE = makeFnl(54321, 1f/128f, 2, 2f, 0.5f);
        BIOME_VARIANT_NOISE = makeFnl(77777, 1f/300f, 3, 2f, 0.5f);
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
        short[] out = new short[H * W];
        for (int i = 0; i < H * W; i++) out[i] = PLAINS;

        if (climate == null || climate.length < 4 * H * W) {
            return out;
        }

        // Generate Perlin noise perturbations
        float[] tempNoise = new float[H * W];
        float[] precipNoiseFact = new float[H * W];
        float[] snowNoise = new float[H * W];

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float nx = j0 + c, ny = i0 + r;
                float tnc = TEMP_NOISE.GetNoise(nx, ny);
                float tnf = TEMP_NOISE_FINE.GetNoise(nx, ny);
                tempNoise[idx] = 0.4f * tnc + 0.2f * tnf;

                float pn = PRECIP_NOISE.GetNoise(nx, ny);
                precipNoiseFact[idx] = 1.0f + 0.2f * pn;

                float snc = SNOW_NOISE.GetNoise(nx, ny);
                float snf = SNOW_NOISE_FINE.GetNoise(nx, ny);
                snowNoise[idx] = 3.0f * snc + 2.0f * snf;
            }
        }

        // Compute slope from padded elevation using Sobel (divide by pixelSizeM for ratio)
        float[] slopeRatio = computeSlopeRatio(elevPadded, H, W, pixelSizeM);

        // Process per-pixel
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float elevVal   = elev[idx];
                float altM      = Math.max(0f, elevVal);
                float slope     = slopeRatio[idx];

                // Climate channels: [0]=temp, [1]=t_season, [2]=precip, [3]=p_cv
                float temp     = climate[idx] + tempNoise[idx];
                float tSeason  = climate[H * W + idx];
                float precip   = Math.max(0f, climate[2 * H * W + idx]) * precipNoiseFact[idx];
                float pCV      = climate[3 * H * W + idx];

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
                float snowTemp = temp + snowNoise[idx];
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

                short biome = PLAINS;

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
                float nx = j0 + c, ny = i0 + r;
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
