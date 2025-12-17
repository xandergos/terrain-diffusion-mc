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
    private static final float DEFAULT_RESOLUTION = 30f;
    private static final float DEFAULT_GAMMA = 1.0f;
    private static final float DEFAULT_C = 30.0f;
    private static final int DEFAULT_SCALE = 2;
    private static final float DEFAULT_NOISE = 1.0f;
    private static final String DEFAULT_API_URL = "http://localhost:8000";

    static {
        loadDefaults();
        Path configPath = resolveConfigPath();
        if (configPath != null) {
            loadOverrides(configPath);
        }
    }

    private TerrainDiffusionConfig() {
    }

    public static float heightConverterResolution() {
        return readFloat("height_converter.resolution", DEFAULT_RESOLUTION) / scale();
    }

    public static float heightConverterGamma() {
        return readFloat("height_converter.gamma", DEFAULT_GAMMA);
    }

    public static float heightConverterC() {
        return readFloat("height_converter.c", DEFAULT_C);
    }

    public static int scale() {
        return readInt("heightmap_api.scale", DEFAULT_SCALE);
    }

    public static float noise() {
        return readFloat("heightmap_api.noise", DEFAULT_NOISE);
    }

    public static String apiUrl() {
        return PROPERTIES.getProperty("heightmap_api.url", DEFAULT_API_URL);
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
            PROPERTIES.setProperty("height_converter.resolution", String.valueOf(DEFAULT_RESOLUTION));
            PROPERTIES.setProperty("heightmap_api.scale", String.valueOf(DEFAULT_SCALE));
            PROPERTIES.setProperty("heightmap_api.url", DEFAULT_API_URL);
            PROPERTIES.setProperty("height_converter.gamma", String.valueOf(DEFAULT_GAMMA));
            PROPERTIES.setProperty("height_converter.c", String.valueOf(DEFAULT_C));
        }
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

    private static int readInt(String key, int defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Invalid int for " + key + ": " + value + ", using default " + defaultValue);
            return defaultValue;
        }
    }
}

