package com.github.xandergos.terraindiffusionmc.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class TerrainDiffusionConfig {
    private static final String FILE_NAME = "terrain-diffusion-mc.properties";
    private static final String RESOURCE_PATH = "/" + FILE_NAME;
    private static final Properties PROPERTIES = new Properties();
    private static final String DEFAULT_INFERENCE_DEVICE = "gpu";
    private static final String BUILD_VARIANT = readBuildVariant();
    private static final boolean DEFAULT_OFFLOAD_MODELS = true;
    private static final boolean DEFAULT_VALIDATE_MODEL = true;
    private static final int DEFAULT_EXPLORER_PORT = 19801;
    private static final int DEFAULT_TILE_SIZE = 256;

    static {
        loadDefaults();
        Path configPath = resolveConfigPath();
        if (configPath != null) {
            loadOverrides(configPath);
        }
    }

    private TerrainDiffusionConfig() {
    }

    /** Inference device: "cpu", "gpu", or "auto" (try GPU then fall back to CPU). */
    public static String inferenceDevice() {
        if ("cpu".equals(BUILD_VARIANT)) {
            return "cpu";
        }
        return readString("inference.device", DEFAULT_INFERENCE_DEVICE);
    }

    /** Whether to offload inactive models from VRAM between pipeline stages. */
    public static boolean offloadModels() {
        return readBoolean("inference.offload_models", DEFAULT_OFFLOAD_MODELS);
    }

    /** TCP port for the local terrain explorer HTTP server. */
    public static int explorerPort() {
        return readInt("explorer.port", DEFAULT_EXPLORER_PORT);
    }

    /** Whether to validate SHA-256 for pre-existing local model files before use. */
    public static boolean validateModel() {
        return readBoolean("validate_model", DEFAULT_VALIDATE_MODEL);
    }

    /** Region side length in blocks. Must be a positive power of 2 (128, 256, 512, ...). */
    public static int tileSize() {
        int configuredTileSize = readInt("tile_size", DEFAULT_TILE_SIZE);
        if (configuredTileSize <= 0 || !isPowerOfTwo(configuredTileSize)) {
            System.err.println("Invalid tile_size: " + configuredTileSize + ", using default " + DEFAULT_TILE_SIZE);
            return DEFAULT_TILE_SIZE;
        }
        return configuredTileSize;
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
            PROPERTIES.setProperty("inference.device", DEFAULT_INFERENCE_DEVICE);
            PROPERTIES.setProperty("validate_model", String.valueOf(DEFAULT_VALIDATE_MODEL));
            PROPERTIES.setProperty("tile_size", String.valueOf(DEFAULT_TILE_SIZE));
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
        try (InputStream defaultConfigInputStream = TerrainDiffusionConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (defaultConfigInputStream != null) {
                Files.copy(defaultConfigInputStream, configPath);
                return;
            }
            System.err.println("Default config resource not found: " + RESOURCE_PATH);
        } catch (IOException e) {
            System.err.println("Failed to copy default config resource: " + e.getMessage());
        }
    }

    private static boolean readBoolean(String key, boolean defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return value != null ? Boolean.parseBoolean(value.trim()) : defaultValue;
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

    private static boolean isPowerOfTwo(int value) {
        return (value & (value - 1)) == 0;
    }

    private static String readBuildVariant() {
        try (InputStream in = TerrainDiffusionConfig.class.getResourceAsStream("/build-variant.properties")) {
            if (in == null) return "unknown";
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("build.variant", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }

}
