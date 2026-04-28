package com.github.xandergos.terraindiffusionmc.basin;

/**
 * Aggregated geometric properties of a single irrigation basin.
 *
 * <p>Phase 1 fields ({@link #averageElevation}, bounding box and {@link #cellCount}) are populated
 * by the Laplacian segmenter via {@link #phase1}. Phase 2 fields ({@link #outletRow},
 * {@link #outletCol} and {@link #downstreamBasinId}) are reserved for the upcoming D8 flow-routing
 * pass and remain at their sentinel values until that stage runs. Not sure about D8 yet but I have to do some test before
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class IrrigationBasin {

    /** Sentinel for a coordinate that has not yet been computed. */
    public static final int UNRESOLVED_COORDINATE = -1;

    /** Sentinel meaning "no downstream basin" (e.g. drains to sea or unresolved). */
    public static final int NO_DOWNSTREAM = -1;

    public final int basinId;
    public final int cellCount;
    public final float averageElevation;

    /** Inclusive bounding box in local segmentation coordinates. */
    public final int minRow;
    public final int minCol;
    public final int maxRow;
    public final int maxCol;

    /** Phase 2 : lowest spillover cell on the basin's ridge boundary. */
    public final int outletRow;
    public final int outletCol;

    /** Phase 2 : basin id this one drains into via its outlet or {@link #NO_DOWNSTREAM}. */
    public final int downstreamBasinId;

    public IrrigationBasin(int basinId, int cellCount, float averageElevation,
                           int minRow, int minCol, int maxRow, int maxCol,
                           int outletRow, int outletCol, int downstreamBasinId) {
        this.basinId = basinId;
        this.cellCount = cellCount;
        this.averageElevation = averageElevation;
        this.minRow = minRow;
        this.minCol = minCol;
        this.maxRow = maxRow;
        this.maxCol = maxCol;
        this.outletRow = outletRow;
        this.outletCol = outletCol;
        this.downstreamBasinId = downstreamBasinId;
    }

    /**
     * Convenience constructor for Phase 1 : outlet and downstream fields default to their sentinels.
     */
    public static IrrigationBasin phase1(int basinId, int cellCount, float averageElevation,
                                         int minRow, int minCol, int maxRow, int maxCol) {
        return new IrrigationBasin(basinId, cellCount, averageElevation,
                minRow, minCol, maxRow, maxCol,
                UNRESOLVED_COORDINATE, UNRESOLVED_COORDINATE, NO_DOWNSTREAM);
    }
}

