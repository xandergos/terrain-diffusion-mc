package com.github.xandergos.terraindiffusionmc.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public final class TerrainDiffusionConfig {
    private static final String FILE_NAME = "terrain-diffusion-mc.properties";
    private static final String RESOURCE_PATH = "/" + FILE_NAME;
    private static final Properties PROPERTIES = new Properties();
    private static final float DEFAULT_GAMMA = 1.0f;
    private static final float DEFAULT_C = 30.0f;
    private static final int DEFAULT_SCALE = 2;
    private static final String DEFAULT_INFERENCE_DEVICE = "auto";

    static {
        loadDefaults();
        Path configPath = resolveConfigPath();
        if (configPath != null) {
            loadOverrides(configPath);
        }
    }

    private TerrainDiffusionConfig() {
    }

    /** Blocks per native 30m pixel. scale=2 → 1 block = 15m. */
    public static int scale() {
        return readInt("scale", DEFAULT_SCALE);
    }

    public static float heightConverterGamma() {
        return readFloat("height_converter.gamma", DEFAULT_GAMMA);
    }

    public static float heightConverterC() {
        return readFloat("height_converter.c", DEFAULT_C);
    }

    /** Inference device: "cpu", "gpu", or "auto" (try GPU then fall back to CPU). */
    public static String inferenceDevice() {
        return readString("inference.device", DEFAULT_INFERENCE_DEVICE);
    }

    private static void loadDefaults() {
        boolean loadedFromResource = false;
        try (InputStream in = TerrainDiffusionConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in != null) {
                PROPERTIES.load(in);
                loadedFromResource = true;
            }
        } catch (IOException e) {
            System.err.println("Failed to load default config from resource: " + e.getMessage());
        }

        if (!loadedFromResource) {
            PROPERTIES.setProperty("scale", String.valueOf(DEFAULT_SCALE));
            PROPERTIES.setProperty("height_converter.gamma", String.valueOf(DEFAULT_GAMMA));
            PROPERTIES.setProperty("height_converter.c", String.valueOf(DEFAULT_C));
            PROPERTIES.setProperty("inference.device", DEFAULT_INFERENCE_DEVICE);
        }
    }

    private static String readString(String key, String defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return value != null ? value.trim().toLowerCase() : defaultValue;
    }

    private static Path resolveConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        } catch (RuntimeException e) {
            System.err.println("Fabric Loader config directory unavailable: " + e.getMessage());
            return null;
        }
    }

    private static void loadOverrides(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.exists(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    Properties overrides = new Properties();
                    overrides.load(in);
                    PROPERTIES.putAll(overrides);
                }
            } else {
                writeConfig(configPath);
            }
        } catch (IOException e) {
            System.err.println("Failed to read config file: " + e.getMessage());
        }
    }

    private static void writeConfig(Path configPath) {
        try (OutputStream out = Files.newOutputStream(configPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            PROPERTIES.store(out, "Terrain Diffusion MC configuration");
        } catch (IOException e) {
            System.err.println("Failed to write config file: " + e.getMessage());
        }
    }

    private static int readInt(String key, int defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Invalid int for " + key + ": " + value + ", using default " + defaultValue);
            return defaultValue;
        }
    }

    private static float readFloat(String key, float defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            System.err.println("Invalid float for " + key + ": " + value + ", using default " + defaultValue);
            return defaultValue;
        }
    }
}
