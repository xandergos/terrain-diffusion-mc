package com.github.xandergos.terraindiffusionmc.debug.river;

public final class TerrainRiverConfig {
    /** Small numerical rise used by priority-flood to route perfectly flat water surfaces. */
    public static final float FLAT_ROUTING_EPSILON = 0.003F;

    /** Boundary sinks are temporary region exits. Oceans still win when present in the region/halo. */
    public static final float BOUNDARY_OUTLET_PENALTY_BLOCKS = 3.0F;

    /** Local runoff is expressed in contributing wet-cell equivalents. */
    public static final float BASE_RUNOFF_INPUT = 0.28F;
    public static final float PRECIPITATION_RUNOFF_SCALE = 1.0F / 900.0F;
    public static final float MAX_SOURCE_INPUT = 0.85F;

    /** Rivers below this discharge remain invisible streams. Visible rivers are meant to be navigable. */
    public static final float MIN_VISIBLE_RIVER_DISCHARGE = 72.0F;
    public static final float MIN_NAVIGABLE_DISCHARGE = 96.0F;

    /** Depression/lake handling. */
    public static final float MIN_LAKE_DEPTH_BLOCKS = 1.25F;
    public static final float MIN_POND_DISCHARGE = 24.0F;
    public static final float LAKE_BALANCE_EVAPORATION_WEIGHT = 0.42F;

    /** Debug/potential display weights. */
    public static final float RIVER_POTENTIAL_DISCHARGE_WEIGHT = 0.70F;
    public static final float RIVER_POTENTIAL_SOURCE_WEIGHT = 0.18F;
    public static final float RIVER_POTENTIAL_LAKE_WEIGHT = 0.12F;

    /** Navigable channel dimensions. */
    public static final float MIN_WIDTH_BLOCKS = 6.5F;
    public static final float MAX_WIDTH_BLOCKS = 28.0F;
    public static final float MIN_DEPTH_BLOCKS = 2.0F;
    public static final float MAX_DEPTH_BLOCKS = 6.5F;

    /** Network cleanup. These values deliberately prefer fewer, stronger, continuous waterways. */
    public static final int GAP_CLOSE_MAX_CELLS = 7;
    public static final float GAP_CLOSE_MIN_DISCHARGE_FRACTION = 0.55F;
    public static final float GAP_CLOSE_MAX_TERRAIN_RISE_BLOCKS = 2.50F;
    public static final int MIN_HEADWATER_BRANCH_LENGTH_CELLS = 12;
    public static final float MIN_HEADWATER_BRANCH_DISCHARGE_FRACTION = 0.78F;
    public static final int MAX_UPSTREAMS_PER_CONFLUENCE = 3;
    public static final int NETWORK_CLEANUP_PASSES = 3;

    /** Meanders appear only where the terrain and biome allow lateral freedom. */
    public static final float MAX_MEANDER_OFFSET_FRACTION_OF_WIDTH = 0.42F;
    public static final float MEANDER_WAVELENGTH_BLOCKS = 58.0F;

    private TerrainRiverConfig() {
    }
}
