package com.github.xandergos.terraindiffusionmc.debug.river.vector;

public final class TerrainRiverVectorConfig {
    public static final float SIMPLIFICATION_TOLERANCE_BLOCKS = 1.25F;
    public static final int SMOOTHING_PASSES = 1;

    public static final int SPATIAL_INDEX_CELL_SIZE_BLOCKS = 32;
    public static final int CHUNK_QUERY_PADDING_BLOCKS = 24;

    public static final float MIN_DEPTH_BLOCKS = 0.35F;
    public static final float MAX_DEPTH_BLOCKS = 3.50F;

    private TerrainRiverVectorConfig() {
    }
}
