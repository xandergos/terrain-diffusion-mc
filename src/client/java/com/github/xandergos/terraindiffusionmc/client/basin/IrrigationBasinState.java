package com.github.xandergos.terraindiffusionmc.client.basin;

/**
 * Client-side runtime state for the irrigation basin (IBM) overlay.
 *
 * <p>All fields are accessed only from the render thread ; plain {@code volatile} flags suffice.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class IrrigationBasinState {

    /** Master switch : when {@code false} the overlay is fully hidden. */
    public static volatile boolean overlayEnabled = false;

    /** When {@code true} ridge cells are rendered as a dark boundary outline. WIP */
    public static volatile boolean showRidges = true;

    /** When {@code true} basin cells are rendered as semi-transparent colored quads. */
    public static volatile boolean showBasinFill = true;

    /** Render radius in tiles around the player (1 = 3×3 tile region). WIP */
    public static volatile int renderRadiusTiles = 1;

    private IrrigationBasinState() {
    }

    /** Flips {@link #overlayEnabled}. */
    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
    }
}
