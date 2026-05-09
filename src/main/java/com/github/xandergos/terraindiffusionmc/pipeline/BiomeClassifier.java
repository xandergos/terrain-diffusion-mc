package com.github.xandergos.terraindiffusionmc.pipeline;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

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

    static {
        TEMP_NOISE = makeFnl(12345, 1f/500f, 3, 2f, 0.5f);
        TEMP_NOISE_FINE = makeFnl(54321, 1f/128f, 2, 2f, 0.5f);
        PRECIP_NOISE = makeFnl(12345, 1f/500f, 5, 2f, 0.5f);
        SNOW_NOISE = makeFnl(12345, 1f/500f, 3, 2f, 0.5f);
        SNOW_NOISE_FINE = makeFnl(54321, 1f/128f, 2, 2f, 0.5f);
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

    // Biome IDs
    static final short PLAINS = 1, SUNFLOWER_PLAINS = 2, SNOWY_PLAINS = 3, ICE_SPIKES = 4, DESERT = 5;
    static final short SWAMP = 6, MANGROVE_SWAMP = 7, FOREST = 8, FLOWER_FOREST = 9, BIRCH_FOREST = 10;
    static final short DARK_FOREST = 11, OLD_GROWTH_BIRCH_FOREST = 12, OLD_GROWTH_PINE_TAIGA = 13, OLD_GROWTH_SPRUCE_TAIGA = 14, TAIGA = 15;
    static final short SNOWY_TAIGA = 16, SAVANNA = 17, SAVANNA_PLATEAU = 18, WINDSWEPT_HILLS = 19, WINDSWEPT_GRAVELY_HILLS = 20;
    static final short WINDSWEPT_FOREST = 21, WINDSWEPT_SAVANNA = 22, JUNGLE = 23, SPARSE_JUNGLE = 24, BAMBOO_JUNGLE = 25;
    static final short BADLANDS = 26, ERODED_BADLANDS = 27, WOODED_BADLANDS = 28, MEADOW = 29, CHERRY_GROVE = 30;
    static final short GROVE = 31, SNOWY_SLOPES = 32, FROZEN_PEAKS = 33, JAGGED_PEAKS = 34, STONY_PEAKS = 35;
    static final short RIVER = 36, FROZEN_RIVER = 37, BEACH = 38, SNOWY_BEACH = 39, STONY_SHORE = 40;
    static final short WARM_OCEAN = 41, LUKEWARM_OCEAN = 42, DEEP_LUKEWARM_OCEAN = 43, OCEAN = 44, DEEP_OCEAN = 45;
    static final short COLD_OCEAN = 46, DEEP_COLD_OCEAN = 47, FROZEN_OCEAN = 48, DEEP_FROZEN_OCEAN = 49, MUSHROOM_FIELDS = 50;
    static final short FOREST_SPARSE = 108, TAIGA_SPARSE = 115, SNOWY_TAIGA_SPARSE = 116;

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
                    else growingSeason = 365f * (0.5f - (float) Math.asin(Math.clamp(x, -1f, 1f)) / (float) Math.PI);
                }

                float gsFactor = Math.clamp((growingSeason - 60f) / (150f - 60f), 0f, 1f);
                float effTreeMoisture = treeMoisture * gsFactor;

                // Slope-dependent bare threshold
                float moistureFactor = Math.clamp((treeMoisture - 0.35f) / 0.45f, 0f, 1f);
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
                boolean isOcean     = elevVal < -5f;
                boolean isDeepOcean = elevVal < -500f;
                boolean mountains   = altM > 2500f;
                boolean lowland     = altM < 200f;
                boolean frozen      = temp < -5f;
                boolean cold        = temp >= -5f && temp < 5f;
                boolean cool        = temp >= 5f  && temp < 12f;
                boolean temperate   = temp >= 12f && temp < 20f;
                boolean warm        = temp >= 20f && temp < 26f;
                boolean hot         = temp >= 26f;

                short biome;

                if (isOcean) {
                    if (isDeepOcean) {
                        if (frozen) biome = DEEP_FROZEN_OCEAN;
                        else if (cold) biome = DEEP_COLD_OCEAN;
                        else if (warm || hot) biome = DEEP_LUKEWARM_OCEAN;
                        else biome = DEEP_OCEAN;
                    } else {
                        if (frozen) biome = FROZEN_OCEAN;
                        else if (cold) biome = COLD_OCEAN;
                        else if (warm || temperate) biome = LUKEWARM_OCEAN;
                        else if (hot) biome = WARM_OCEAN;
                        else biome = OCEAN;
                    }
                } else if (lowland) {
                    if (frozen || cold) {
                        if (isSteep) biome = STONY_SHORE;
                        else {
                            if (hasSnow) {
                                if (treesNone) biome = SNOWY_BEACH;
                                else if (treesSparse) biome = SNOWY_TAIGA_SPARSE;
                                else if (treesDense || treesForest) biome = SNOWY_TAIGA;
                                else biome = SNOWY_BEACH;
                            } else {
                                if (treesNone) biome = BEACH;
                                else if (treesSparse) biome = TAIGA_SPARSE;
                                else if (treesDense || treesForest) biome = TAIGA;
                                else biome = BEACH;
                            }
                        }
                    } else if (cool) {
                        if (isSteep) biome = STONY_SHORE;
                        else {
                            if (treesNone) biome = STONY_SHORE;
                            else if (treesSparse) biome = PLAINS;
                            else if (treesDense) biome = FOREST_SPARSE;
                            else if (treesForest || treesRainforest) biome = BIRCH_FOREST;
                            else biome = STONY_SHORE;
                        }
                    } else if (temperate) {
                        if (isSteep) biome = STONY_SHORE;
                        else if (slopeBare) {
                            if (treesNone || treesSparse) biome = BEACH;
                            else if (treesDense) biome = BIRCH_FOREST;
                            else if (treesForest) biome = FOREST;
                            else if (treesRainforest) biome = JUNGLE;
                            else biome = BEACH;
                        } else if (slopeMedium) {
                            if (treesNone || treesSparse) biome = STONY_SHORE;
                            else if (treesDense) biome = FOREST_SPARSE;
                            else if (treesForest) biome = FOREST;
                            else if (treesRainforest) biome = JUNGLE;
                            else biome = STONY_SHORE;
                        } else {
                            if (elevVal <= 75f) biome = BEACH;
                            else biome = PLAINS;
                        }
                    } else if (warm) {
                        if (isSteep) biome = STONY_SHORE;
                        else if (slopeBare) {
                            if (treesNone || treesSparse) biome = BEACH;
                            else biome = SAVANNA_PLATEAU;
                        } else if (slopeMedium) {
                            if (treesNone || treesSparse) biome = DESERT;
                            else biome = SAVANNA;
                        } else {
                            if (elevVal <= 75f) biome = BEACH;
                            else biome = SUNFLOWER_PLAINS;
                        }
                    } else {
                        if (isSteep) biome = STONY_SHORE;
                        else if (slopeBare) {
                            if (treesNone || treesSparse) biome = BADLANDS;
                            else biome = DESERT;
                        } else if (slopeMedium) {
                            if (treesNone || treesSparse) biome = BADLANDS;
                            else if (treesDense) biome = SAVANNA;
                            else biome = DESERT;
                        } else {
                            if (elevVal <= 75f) biome = DESERT;
                            else biome = ERODED_BADLANDS;
                        }
                    }
                } else {
                    biome = PLAINS;
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

    public static MutableText getDebugText(float elev, float temp, float tSeason, float precip, float pCV, float slope) {
        MutableText text = Text.literal("=== Biome Classification Debug ===\n");

        float tStd = tSeason / 100f;
        float tEff = Math.max(0f, temp + 0.5f * tStd);
        float pet = Math.max(250f, 250f + 25f * tEff + 0.7f * tEff * tEff);
        float aridity = precip / Math.max(1f, pet);
        float seasonPenalty = 1f - 0.35f * Math.min(1f, pCV / 100f);
        float treeMoisture = aridity * seasonPenalty;

        float amplitude = tStd * 1.414f;
        float growingSeason;
        if (amplitude < 0.1f) {
            growingSeason = temp > 5f ? 365f : 0f;
        } else {
            float x = (5f - temp) / amplitude;
            if (x <= -1f) growingSeason = 365f;
            else if (x >= 1f) growingSeason = 0f;
            else growingSeason = 365f * (0.5f - (float) Math.asin(Math.clamp(x, -1f, 1f)) / (float) Math.PI);
        }

        float gsFactor = Math.clamp((growingSeason - 60f) / (150f - 60f), 0f, 1f);
        float effTreeMoisture = treeMoisture * gsFactor;

        float moistureFactor = Math.clamp((treeMoisture - 0.35f) / 0.45f, 0f, 1f);
        float bareThreshold = 0.7f + (1.19f - 0.7f) * moistureFactor;

        boolean treesNone = effTreeMoisture < 0.2f;
        boolean tooArid = treeMoisture < 0.05f;
        boolean tooCold = growingSeason < 60f;
        boolean barren = tooArid || tooCold;
        boolean treesSparse = !treesNone && effTreeMoisture < 0.5f;
        boolean treesForest = !treesNone && effTreeMoisture >= 0.5f && effTreeMoisture < 0.8f;
        boolean treesDense = !treesNone && effTreeMoisture >= 0.8f && effTreeMoisture < 1.3f;
        boolean treesRainforest = !treesNone && effTreeMoisture >= 1.3f;

        boolean slopeMedium = slope >= 0.62f && slope < bareThreshold;
        boolean slopeBare = slope >= bareThreshold;

        boolean isSteep = slope > 0.78f;
        boolean hasSnow = temp < 0f && precip > 150f && !isSteep;

        boolean isOcean = elev < 0f;
        boolean isDeepOcean = elev < -100f;
        boolean mountains = elev > 2500f;
        boolean lowland = elev < 200f;
        boolean frozen = temp < -5f;
        boolean cold = temp >= -5f && temp < 5f;
        boolean cool = temp >= 5f && temp < 12f;
        boolean temperate = temp >= 12f && temp < 20f;
        boolean warm = temp >= 20f && temp < 26f;
        boolean hot = temp >= 26f;

        text.append(Text.literal("=== Derived Values ===\n"));
        text.append(Text.literal("treeMoisture: " + treeMoisture + "\n"));
        text.append(Text.literal("effTreeMoisture: " + effTreeMoisture + "\n"));
        text.append(Text.literal("growingSeason: " + growingSeason + "\n"));
        text.append(Text.literal("bareThreshold: " + bareThreshold + "\n"));
        text.append(Text.literal("\n=== Tree Coverage ===\n"));
        text.append(Text.literal("treesNone: " + treesNone + "\n"));
        text.append(Text.literal("tooArid: " + tooArid + "\n"));
        text.append(Text.literal("tooCold: " + tooCold + "\n"));
        text.append(Text.literal("barren: " + barren + "\n"));
        text.append(Text.literal("treesSparse: " + treesSparse + "\n"));
        text.append(Text.literal("treesForest: " + treesForest + "\n"));
        text.append(Text.literal("treesDense: " + treesDense + "\n"));
        text.append(Text.literal("treesRainforest: " + treesRainforest + "\n"));
        text.append(Text.literal("\n=== Slope ===\n"));
        text.append(Text.literal("slopeMedium: " + slopeMedium + "\n"));
        text.append(Text.literal("slopeBare: " + slopeBare + "\n"));
        text.append(Text.literal("\n=== Snow ===\n"));
        text.append(Text.literal("isSteep: " + isSteep + "\n"));
        text.append(Text.literal("hasSnow: " + hasSnow + "\n"));
        text.append(Text.literal("\n=== Elevation/Temp ===\n"));
        text.append(Text.literal("isOcean: " + isOcean + "\n"));
        text.append(Text.literal("isDeepOcean: " + isDeepOcean + "\n"));
        text.append(Text.literal("mountains: " + mountains + "\n"));
        text.append(Text.literal("lowland: " + lowland + "\n"));
        text.append(Text.literal("frozen: " + frozen + "\n"));
        text.append(Text.literal("cold: " + cold + "\n"));
        text.append(Text.literal("cool: " + cool + "\n"));
        text.append(Text.literal("temperate: " + temperate + "\n"));
        text.append(Text.literal("warm: " + warm + "\n"));
        text.append(Text.literal("hot: " + hot + "\n"));
        text.append(Text.literal("\n=== End Debug ==="));

        return text;
    }
}
