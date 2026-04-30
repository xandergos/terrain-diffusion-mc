package com.github.xandergos.terraindiffusionmc.hydro;

/**
 * Shared constants for the hydrological pipeline.
 *
 * <p>The pipeline operates on a heightmap, "fills" closed depressions so every land cell has a
 * downhill path to the sea, computes per-cell D8 flow directions and then accumulates upstream
 * contributing area to derive river size. Cells with elevation at or below {@link #SEA_LEVEL_Y}
 * are absorbed by the sea : they act as boundary sinks for the flow accumulation pass.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyConstants {

    /** Minecraft Y coordinate at which the sea sits ; cells at or below this elevation are sinks. */
    public static final int SEA_LEVEL_Y = 63;

    /** Sentinel direction value indicating the cell drains into the sea (or off-tile). */
    public static final byte FLOW_DIR_SEA = -1;

    /** D8 row offsets indexed 0..7 (N, NE, E, SE, S, SW, W, NW). */
    public static final int[] D8_DROW = { -1, -1,  0,  1,  1,  1,  0, -1 };

    /** D8 column offsets indexed 0..7 (N, NE, E, SE, S, SW, W, NW). */
    public static final int[] D8_DCOL = {  0,  1,  1,  1,  0, -1, -1, -1 };

    /** Cardinal-vs-diagonal step length used to weight slope when picking D8 direction. */
    public static final double[] D8_INVERSE_DISTANCE = {
            1.0, 1.0 / Math.sqrt(2.0), 1.0, 1.0 / Math.sqrt(2.0),
            1.0, 1.0 / Math.sqrt(2.0), 1.0, 1.0 / Math.sqrt(2.0)
    };

    private HydrologyConstants() {}
}
