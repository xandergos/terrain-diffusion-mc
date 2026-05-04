package com.github.xandergos.terraindiffusionmc.world;

/**
 * Stable river render classes shared by terrain generation and chunk decoration.
 *
 * <p>This deliberately contains data codes only. Worldgen decoration should depend
 * on these classes not on the hydrology implementation that produced them.</p>
 */
public final class RiverCellClass {
    public static final byte NONE = 0;
    /** Carved bank or floodplain shoulder. No standing water is placed here. */
    public static final byte BANK = 1;
    /** Very small rill/stream. Rendered as a narrow waterlogged sediment layer. */
    public static final byte TINY_STREAM = 2;
    /** Small stream. Rendered primarily as a waterlogged bed layer. */
    public static final byte SMALL_STREAM = 3;
    /** Normal river. Rendered with full water above the carved bed. */
    public static final byte FULL_RIVER = 4;

    private RiverCellClass() {
    }
}
