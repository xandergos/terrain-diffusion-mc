package com.github.xandergos.terraindiffusionmc.debug.flow;

public final class TerrainFlowConfig {
    /**
     * Height differences are expressed in pipeline base meters : not Minecraft Y.
     * Strictly downhill routing avoids flat cycles in the debug accumulation pass.
     */
    public static final float MIN_DOWNHILL_METERS = 0.001F;

    /**
     * Kept for future depression breaching. Uphill candidates are rejected before scoring for now.
     */
    public static final float UPHILL_PENALTY = 50.0F;

    /**
     * Cost must be strong enough to make the flow respect the cost map.
     */
    public static final float TERRAIN_COST_WEIGHT = 1.65F;

    /**
     * Downhill is only a bounded preference. It must not turn the solver into steepest descent.
     */
    public static final float DOWNHILL_BONUS = 0.24F;

    /**
     * Drop where the downhill bonus is mostly saturated.
     */
    public static final float PREFERRED_DROP_METERS = 2.0F;

    /**
     * Additional penalty for very steep drops. This discourages cliff cutting when a valley exists.
     */
    public static final float STEEP_DROP_START_METERS = 3.0F;
    public static final float STEEP_DROP_END_METERS = 14.0F;
    public static final float STEEP_DROP_PENALTY = 0.42F;

    /**
     * Small penalty to reduce diagonal jitter in the D8 field.
     */
    public static final float DIAGONAL_PENALTY = 0.035F;

    /**
     * Secondary attraction toward cells that already receive upstream flow in pass 0.
     * Keep this low. If it gets too high, the changed-by-convergence overlay lights up everywhere
     * and the network stops reflecting the cost map.
     */
    public static final float CONVERGENCE_BONUS_WEIGHT = 0.08F;

    /**
     * Ignore tiny convergence differences when marking a cell as changed by convergence.
     */
    public static final float MIN_CONVERGENCE_GAIN_FOR_CHANGE = 0.18F;

    /**
     * Tiny cost differences are visual noise. Cost direction is only meaningful above this delta.
     */
    public static final float MIN_COST_DROP = 0.008F;

    /**
     * Cost drop where the cost field strongly agrees with a valley/channel decision.
     */
    public static final float STRONG_COST_DROP = 0.18F;

    /**
     * Flow drop range used by the hydro/cost shaping diagnostics.
     */
    public static final float RIVER_SHAPE_DROP_START_METERS = 0.20F;
    public static final float RIVER_SHAPE_DROP_END_METERS = 2.50F;

    /**
     * Accumulation range used by the hydro/cost shaping diagnostics.
     */
    public static final float RIVER_SHAPE_LOG_START = 0.56F;
    public static final float RIVER_SHAPE_LOG_END = 0.86F;

    /**
     * Debug extraction thresholds. These are intentionally softer than the old river preview;
     * the new score requires accumulation + drop + cost agreement instead of accumulation alone.
     */
    public static final float RIVER_SHAPE_SCORE_THRESHOLD = 0.22F;
    public static final float MISSING_RIVER_COST_THRESHOLD = 0.46F;

    /**
     * Do not preview rivers from tiny one-cell or short local flows.
     */
    public static final float RIVER_PREVIEW_MIN_ACCUMULATION = 32.0F;

    /**
     * Debug-only river preview threshold on log-normalized final accumulation.
     * This is not the final river extraction threshold yet.
     */
    public static final float RIVER_PREVIEW_LOG_THRESHOLD = 0.70F;

    private TerrainFlowConfig() {
    }
}
