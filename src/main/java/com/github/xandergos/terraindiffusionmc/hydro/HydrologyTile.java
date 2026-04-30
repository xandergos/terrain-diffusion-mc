package com.github.xandergos.terraindiffusionmc.hydro;

/**
 * Computed hydrology data for a single heightmap tile.
 *
 * <p>Holds everything the renderer needs to draw rivers and debug overlays plus the boundary flux
 * arrays consumed by cross-tile stitching :
 * <ul>
 *   <li>raw and pit-filled heightmaps (raw to drape rendering and filled for diff debug)</li>
 *   <li>per-cell D8 {@link #flowDirection} encoded as bytes 0..7 or
 *       {@link HydrologyConstants#FLOW_DIR_SEA}</li>
 *   <li>per-cell {@link #flowAccumulation} after combining incoming neighbor flux</li>
 *   <li>{@link #outgoingFluxNorth}, {@link #outgoingFluxSouth}, {@link #outgoingFluxWest},
 *       {@link #outgoingFluxEast} : at each cell of a given edge for how much flow leaves the tile
 *       into the corresponding neighbor. The neighbor's stitching pass uses these values as
 *       extra in-flow during its own accumulation pass.</li>
 * </ul>
 *
 * <p>Coordinate convention matches the rest of the codebase : {@code row} = Z axis
 * and {@code col} = X axis. World-space block coordinate of cell (0, 0) is stored
 * in {@link #originBlockI}/{@link #originBlockJ}.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyTile {

    public final int originBlockI;
    public final int originBlockJ;
    public final int tileI;
    public final int tileJ;
    public final int width;
    public final int height;

    public final short[][] heightmap;
    public final short[][] filledHeightmap;
    public final byte[] flowDirection;
    public final int[] flowAccumulation;

    /**
     * Outgoing flux through the north edge ({@code row = 0}). One entry per column. Value is the
     * accumulation that the cell at row 0 forwards to {@code (row=-1, col)} when its D8 direction
     * crosses the north boundary otherwise 0.
     */
    public final int[] outgoingFluxNorth;
    /** Outgoing flux through the south edge ({@code row = height - 1}). */
    public final int[] outgoingFluxSouth;
    /** Outgoing flux through the west edge ({@code col = 0}). One entry per row. */
    public final int[] outgoingFluxWest;
    /** Outgoing flux through the east edge ({@code col = width - 1}). One entry per row. */
    public final int[] outgoingFluxEast;

    /**
     * Per-cell {@link HydrologicalFeature} ordinal, row-major. Filled by
     * {@link HydrologyClassifier} during build. Renderer and any future block placer read from
     * here.
     */
    public final byte[] features;

    public HydrologyTile(int originBlockI, int originBlockJ, int tileI, int tileJ,
                         int width, int height,
                         short[][] heightmap, short[][] filledHeightmap,
                         byte[] flowDirection, int[] flowAccumulation,
                         int[] outgoingFluxNorth, int[] outgoingFluxSouth,
                         int[] outgoingFluxWest, int[] outgoingFluxEast,
                         byte[] features) {
        this.originBlockI = originBlockI;
        this.originBlockJ = originBlockJ;
        this.tileI = tileI;
        this.tileJ = tileJ;
        this.width = width;
        this.height = height;
        this.heightmap = heightmap;
        this.filledHeightmap = filledHeightmap;
        this.flowDirection = flowDirection;
        this.flowAccumulation = flowAccumulation;
        this.outgoingFluxNorth = outgoingFluxNorth;
        this.outgoingFluxSouth = outgoingFluxSouth;
        this.outgoingFluxWest = outgoingFluxWest;
        this.outgoingFluxEast = outgoingFluxEast;
        this.features = features;
    }
}