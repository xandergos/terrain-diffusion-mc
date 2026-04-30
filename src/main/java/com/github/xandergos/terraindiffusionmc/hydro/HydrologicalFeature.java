package com.github.xandergos.terraindiffusionmc.hydro;

/**
 * Classification of a single hydrological cell.
 *
 * <p>Each value is stored as a byte in {@link HydrologyTile#features} so the per-cell footprint
 * stays small. Adding a new feature later (delta, lake, meander, oxbow, ect...) is a matter of
 * appending a constant here, teaching {@link HydrologyClassifier} to emit it and adding a
 * color or block mapping wherever it's consumed.
 *
 * <p>Order is significant : {@link #ordinal()} is the on-disk and on-tile representation.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public enum HydrologicalFeature {

    /** Default : land cell with no significant water flow. */
    NONE,

    /** Small upland watercourse ; not yet a proper river. Rendered cyan. */
    STREAM,

    /** Mid-size watercourse ; the bulk of any visible drainage network. Rendered water blue. */
    RIVER,

    /** Major watercourse leading to the sea. Rendered red. */
    TRUNK_RIVER,

    /** Sea cell within the discharge plume of a trunk river. Rendered lime. */
    RIVER_MOUTH;

    private static final HydrologicalFeature[] VALUES = values();

    public static HydrologicalFeature fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) return NONE;
        return VALUES[ordinal];
    }
}