package com.github.xandergos.terraindiffusionmc.debug.river;

public final class TerrainRiverConfig {
    /** Minimum local contributing cells before a river candidate is allowed. */
    public static final float MIN_ACCUMULATION = 32.0F;

    /** Log-normalized final accumulation threshold for debug river extraction. */
    public static final float LOG_THRESHOLD = 0.70F;

    public static final float MIN_WIDTH_BLOCKS = 2.0F;
    public static final float MAX_WIDTH_BLOCKS = 16.0F;

    private TerrainRiverConfig() {
    }
}
