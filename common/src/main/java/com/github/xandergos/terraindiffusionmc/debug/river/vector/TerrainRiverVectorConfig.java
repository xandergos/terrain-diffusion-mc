package com.github.xandergos.terraindiffusionmc.debug.river.vector;

public final class TerrainRiverVectorConfig {
    public static final float SIMPLIFICATION_TOLERANCE_BLOCKS = 0.45F;
    public static final float MAX_VECTOR_SEGMENT_LENGTH_BLOCKS = 5.0F;
    public static final int SMOOTHING_PASSES = 2;
    public static final float MAX_SMOOTHING_OFFSET_FRACTION_OF_WIDTH = 0.30F;
    public static final int MAX_SMOOTHING_TERRAIN_RISE_BLOCKS = 2;

    public static final int SPATIAL_INDEX_CELL_SIZE_BLOCKS = 32;
    public static final int CHUNK_QUERY_PADDING_BLOCKS = 24;
    public static final int LOCAL_TILE_VECTOR_HALO_BLOCKS = 192;

    /** Close, near-parallel weaker vector channels are collapsed into the stronger nearby channel. */
    public static final float PARALLEL_MERGE_RADIUS_BLOCKS = 7.0F;
    public static final float PARALLEL_MERGE_MIN_NEAR_FRACTION = 0.82F;
    public static final float PARALLEL_MERGE_MIN_DIRECTION_DOT = 0.58F;
    public static final float PARALLEL_MERGE_MAX_RELATIVE_DISCHARGE = 1.05F;
    public static final int PARALLEL_MERGE_MAX_VERTICAL_DELTA_BLOCKS = 5;
    public static final float PARALLEL_MERGE_ENDPOINT_RADIUS_MULTIPLIER = 1.35F;

    public static final float MIN_DEPTH_BLOCKS = 0.35F;
    public static final float MAX_DEPTH_BLOCKS = 3.50F;

    private TerrainRiverVectorConfig() {
    }
}
