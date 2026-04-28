package com.github.xandergos.terraindiffusionmc.basin;

/**
 * Computed irrigation basin mesh (IBM) for a single heightmap tile.
 *
 * <p>Holds everything the renderer needs:
 * <ul>
 *   <li>per-cell basin labels (drives the semi-transparent fill)</li>
 *   <li>per-cell ridge detection via {@link #isRidge} (drives the boundary outline)</li>
 *   <li>per-basin metadata (bounding box, mean elevation, cell count)</li>
 *   <li>the source heightmap so the renderer can drape quads on the terrain surface</li>
 * </ul>
 *
 * <p>All coordinate fields are local to the tile. The world-space origin is stored in
 * {@link #originBlockI}/{@link #originBlockJ} so the same layout applies to any tile position.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class IrrigationBasinMesh {

    /** World-space block coordinate of cell (0, 0). {@code i} = Z, {@code j} = X matching LocalTerrainProvider. */
    public final int originBlockI;
    public final int originBlockJ;

    public final int width;
    public final int height;

    /** Source elevation samples (block units) and row-major {@code [row][col]}. */
    public final short[][] heightmap;

    /** Per-cell basin id, row-major ; {@link LaplacianBasinSegmenter#LABEL_RIDGE} on ridge cells. */
    public final int[] labels;

    /** Per-basin properties indexed by basin id. */
    public final IrrigationBasin[] basins;

    public IrrigationBasinMesh(int originBlockI, int originBlockJ, int width, int height,
                               short[][] heightmap, int[] labels, IrrigationBasin[] basins) {
        this.originBlockI = originBlockI;
        this.originBlockJ = originBlockJ;
        this.width = width;
        this.height = height;
        this.heightmap = heightmap;
        this.labels = labels;
        this.basins = basins;
    }

    /**
     * Returns the basin id at the given local cell or {@link LaplacianBasinSegmenter#LABEL_RIDGE}.
     */
    public int labelAt(int row, int col) {
        return labels[row * width + col];
    }

    /**
     * Returns {@code true} if the cell sits on a ridge (basin boundary).
     */
    public boolean isRidge(int row, int col) {
        return labels[row * width + col] == LaplacianBasinSegmenter.LABEL_RIDGE;
    }
}