package com.github.xandergos.terraindiffusionmc.client.hydro;

/**
 * Client-side toggles for the hydrology debug overlay. All fields are {@code volatile} so the
 * settings screen and key handlers can mutate them from any thread without coordination.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyState {

    /** Master toggle : when {@code false} no overlay is drawn at all. */
    public static volatile boolean overlayEnabled = false;

    /** When {@code true} draw rivers as continuous polylines whose width grows with flow accumulation. */
    public static volatile boolean showRivers = true;

    /** When {@code true} draw a small arrow per cell pointing toward the D8 downstream neighbor. */
    public static volatile boolean showFlowArrows = false;

    /** When {@code true} highlight cells that were raised by the pit filler (debug : where dams formed). */
    public static volatile boolean showFilledDelta = false;

    /** Block radius around the player to render. */
    public static volatile int renderRadius = 256;

    /** Flips {@link #overlayEnabled}. Used by the toggle keybinding. */
    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
    }

    private HydrologyState() {}
}