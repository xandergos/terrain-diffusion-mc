package com.github.xandergos.terraindiffusionmc.pipeline;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads model-specific WorldPipeline constants from a JSON resource.
 *
 * <p>The file format mirrors the Python WorldPipeline config so swapping
 * to a different trained model can be done by replacing a single config file.
 */
public final class WorldPipelineModelConfig {
    private static final String CONFIG_FILE_NAME = "world_pipeline_config.json";
    private static final Gson GSON = new Gson();
    private static final ConfigJson CONFIG = loadConfig();

    private WorldPipelineModelConfig() {
    }

    /** Native meters-per-pixel resolution used by the model outputs. */
    public static float nativeResolution() {
        return CONFIG.nativeResolution;
    }

    /** Latent compression factor between native and latent grids. */
    public static int latentCompression() {
        return CONFIG.latentCompression;
    }

    /** Coarse output denormalization means (6 channels). */
    public static float[] coarseMeans() {
        return CONFIG.coarseMeans.clone();
    }

    /** Coarse output denormalization stds (6 channels). */
    public static float[] coarseStds() {
        return CONFIG.coarseStds.clone();
    }

    /** Per-conditioning-channel SNR values (5 channels). */
    public static float[] conditioningSnr() {
        return CONFIG.condSnr.clone();
    }

    /** Synthetic map frequency multipliers (5 channels). */
    public static float[] frequencyMultipliers() {
        return CONFIG.frequencyMult.clone();
    }

    /** Residual output mean used at decode time. */
    public static float residualMean() {
        return CONFIG.residualMean;
    }

    /** Residual output std used at decode time. */
    public static float residualStd() {
        return CONFIG.residualStd;
    }

    /** Coarse pooling factor in the source Python pipeline config. */
    public static int coarsePooling() {
        return CONFIG.coarsePooling;
    }

    /** Coarse elevation pooling mode metadata from pipeline config. */
    public static String elevCoarsePoolMode() {
        return CONFIG.elevCoarsePoolMode;
    }

    /** Coarse p5 pooling mode metadata from pipeline config. */
    public static String p5CoarsePoolMode() {
        return CONFIG.p5CoarsePoolMode;
    }

    /** Histogram raw metadata from pipeline config; may be null. */
    public static float[] histogramRaw() {
        return CONFIG.histogramRaw == null ? null : CONFIG.histogramRaw.clone();
    }

    /** Synthetic-map drop-water percentage metadata from pipeline config. */
    public static float dropWaterPercent() {
        return CONFIG.dropWaterPercent;
    }

    private static ConfigJson loadConfig() {
        try {
            ModelAssetManager.ensureAssetsReady();
            Path configPath = ModelAssetManager.resolveAssetPath(CONFIG_FILE_NAME);
            try (Reader configReader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                ConfigJson config = GSON.fromJson(configReader, ConfigJson.class);
                validateConfig(config);
                return config;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load model config from " + CONFIG_FILE_NAME, exception);
        }
    }

    private static void validateConfig(ConfigJson config) {
        if (config == null) {
            throw new IllegalArgumentException("Model config is empty");
        }
        requireArrayLength(config.coarseMeans, 6, "coarse_means");
        requireArrayLength(config.coarseStds, 6, "coarse_stds");
        requireArrayLength(config.condSnr, 5, "cond_snr");
        requireArrayLength(config.frequencyMult, 5, "frequency_mult");
        if (config.coarsePooling <= 0) {
            throw new IllegalArgumentException("coarse_pooling must be > 0");
        }
        if (config.histogramRaw != null && config.histogramRaw.length != 5) {
            throw new IllegalArgumentException("histogram_raw must contain exactly 5 values when provided");
        }
        if (config.latentCompression <= 0) {
            throw new IllegalArgumentException("latent_compression must be > 0");
        }
        if (config.nativeResolution <= 0f) {
            throw new IllegalArgumentException("native_resolution must be > 0");
        }
    }

    private static void requireArrayLength(float[] values, int expectedLength, String fieldName) {
        if (values == null || values.length != expectedLength) {
            throw new IllegalArgumentException(fieldName + " must contain exactly " + expectedLength + " values");
        }
    }

    private static final class ConfigJson {
        @SerializedName("_class_name")
        String className;

        @SerializedName("_diffusers_version")
        String diffusersVersion;

        @SerializedName("coarse_means")
        float[] coarseMeans;

        @SerializedName("coarse_pooling")
        int coarsePooling;

        @SerializedName("coarse_stds")
        float[] coarseStds;

        @SerializedName("cond_snr")
        float[] condSnr;

        @SerializedName("drop_water_pct")
        float dropWaterPercent;

        @SerializedName("elev_coarse_pool_mode")
        String elevCoarsePoolMode;

        @SerializedName("frequency_mult")
        float[] frequencyMult;

        @SerializedName("histogram_raw")
        float[] histogramRaw;

        @SerializedName("latent_compression")
        int latentCompression;

        @SerializedName("native_resolution")
        float nativeResolution;

        @SerializedName("p5_coarse_pool_mode")
        String p5CoarsePoolMode;

        @SerializedName("residual_mean")
        float residualMean;

        @SerializedName("residual_std")
        float residualStd;
    }
}
