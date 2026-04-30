package com.github.xandergos.terraindiffusionmc.client.hydro;

import com.github.xandergos.terraindiffusionmc.hydro.HydrologicalFeature;

/**
 * Maps each {@link HydrologicalFeature} to the ARGB color used by the renderer.
 *
 * <p>The color palette mirrors the eventual block placement plan : streams and trunk rivers
 * use Minecraft cyan and red wool tints, river mouths use lime wool and standard rivers use
 * the vanilla water tint. This makes the debug overlay a faithful preview of what the world
 * will look like once the chunk-gen placement pass is wired in.
 *
 * <p>Add a new mapping here when you add a new feature constant ; the renderer will pick it
 * up automatically on its next pass.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class FeatureColors {

    /** Alpha channel applied to every feature color (semi-transparent for the overlay). */
    private static final int ALPHA = 0xCC;

    /** Cyan wool tint used for streams. */
    private static final int CYAN_WOOL  = 0x158991;
    /** Vanilla water tint used for standard rivers. */
    private static final int WATER_BLUE = 0x3F76E4;
    /** Red wool tint used for trunk rivers. */
    private static final int RED_WOOL   = 0xA12722;
    /** Lime wool tint used for river mouths. */
    private static final int LIME_WOOL  = 0x70B919;

    private FeatureColors() {}

    /**
     * Returns an ARGB integer for the given feature.
     *
     * @return packed color ; {@code 0} for {@link HydrologicalFeature#NONE} so callers can use
     *         the return value as a "skip this cell" signal.
     */
    public static int colorFor(HydrologicalFeature feature) {
        return switch (feature) {
            case STREAM      -> argb(CYAN_WOOL);
            case RIVER       -> argb(WATER_BLUE);
            case TRUNK_RIVER -> argb(RED_WOOL);
            case RIVER_MOUTH -> argb(LIME_WOOL);
            case NONE        -> 0;
        };
    }

    private static int argb(int rgb) {
        return (ALPHA << 24) | (rgb & 0x00FFFFFF);
    }
}