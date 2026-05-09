package com.github.xandergos.terraindiffusionmc.debug.cost;

public final class TerrainCostConfig {
    public static final float BASE_COST = 0.18F;

    // These thresholds are expressed in pipeline base meters per block.
    // The previous version used Minecraft Y and thresholds that were too high,
    // which flattened the slope/ridge/valley debug overlays.
    public static final float SLOPE_START = 0.12F;
    public static final float SLOPE_END = 1.60F;
    public static final float SLOPE_WEIGHT = 0.55F;

    public static final float RIDGE_START = 0.10F;
    public static final float RIDGE_END = 1.10F;
    public static final float RIDGE_WEIGHT = 0.60F;

    public static final float VALLEY_START = 0.08F;
    public static final float VALLEY_END = 0.95F;
    public static final float VALLEY_WEIGHT = 0.45F;

    public static final float DEFAULT_BIOME_COST = 0.08F;
    public static final float FORBIDDEN_COST = 10.0F;

    private TerrainCostConfig() {
    }
}
