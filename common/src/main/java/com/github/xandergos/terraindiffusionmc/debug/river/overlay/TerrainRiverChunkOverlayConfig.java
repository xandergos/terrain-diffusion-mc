package com.github.xandergos.terraindiffusionmc.debug.river.overlay;

public final class TerrainRiverChunkOverlayConfig {
    public static final int CHUNK_SIZE_BLOCKS = 16;

    /**
     * Debug-only margin used to build a local vector network before applying the overlay to a full tile.
     * The overlay itself is now rendered over the terrain tile : not just the current Minecraft chunk.
     */
    public static final int LOCAL_NETWORK_MARGIN_BLOCKS = 160;

    public static final int SEGMENT_QUERY_PADDING_BLOCKS = 32;

    public static final float MIN_CHANNEL_RADIUS_BLOCKS = 1.25F;
    public static final float BANK_EXTRA_BLOCKS = 3.50F;
    public static final float WET_EXTRA_BLOCKS = 5.00F;
    public static final float VEGETATION_EXTRA_BLOCKS = 7.00F;

    public static final float MAX_TERRAIN_CORRECTION_BLOCKS = 5.50F;
    public static final float BANK_RAISE_PREVIEW_BLOCKS = 0.55F;

    private TerrainRiverChunkOverlayConfig() {
    }
}
