package com.github.xandergos.terraindiffusionmc.hydro;

/**
 * Computes the D8 flow direction for each cell of a (pit-filled) heightmap.
 *
 * <p>D8 is the classic single-direction flow model : from each cell water flows to the one of its
 * 8 neighbors that has the steepest descent. Slope is weighted by Euclidean step length so a
 * diagonal neighbor 1 block lower is preferred to a cardinal neighbor 1 block lower
 * (steeper slope per unit distance).
 *
 * <p>Cells at or below {@link HydrologyConstants#SEA_LEVEL_Y} get the sentinel direction
 * {@link HydrologyConstants#FLOW_DIR_SEA} : they act as sinks and stop the flow. Cells on the
 * tile boundary that have no lower neighbor also receive that sentinel ; they spill off the tile
 * and water leaves our area of interest.
 *
 * <p>Because the heightmap was pit-filled, every interior land cell is guaranteed to have at
 * least one neighbor at or below its own elevation. Where that's a strict equality (a flat
 * region), still pick the neighbor with the lowest elevation ; ties are broken by index
 * order which gives deterministic but visually arbitrary directions on flats. For rivers the
 * effect is invisible because flats produce no flow accumulation worth rendering.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class FlowDirection {

    private FlowDirection() {}

    /**
     * Builds a per-cell direction array sized {@code width * height}.
     *
     * @param filledHeightmap heightmap output of {@link PitFiller#fill}
     * @param width           number of columns
     * @param height          number of rows
     * @return row-major array of D8 direction indices (0..7) or {@link HydrologyConstants#FLOW_DIR_SEA}
     */
    public static byte[] compute(short[][] filledHeightmap, int width, int height) {
        byte[] direction = new byte[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int idx = row * width + col;
                int centre = filledHeightmap[row][col];
                if (centre <= HydrologyConstants.SEA_LEVEL_Y) {
                    direction[idx] = HydrologyConstants.FLOW_DIR_SEA;
                    continue;
                }

                int bestDir = -1;
                double bestSlope = 0.0;
                for (int k = 0; k < 8; k++) {
                    int nr = row + HydrologyConstants.D8_DROW[k];
                    int nc = col + HydrologyConstants.D8_DCOL[k];
                    if (nr < 0 || nr >= height || nc < 0 || nc >= width) continue;
                    int neighbourElev = filledHeightmap[nr][nc];
                    if (neighbourElev >= centre) continue;
                    double slope = (centre - neighbourElev) * HydrologyConstants.D8_INVERSE_DISTANCE[k];
                    if (slope > bestSlope) {
                        bestSlope = slope;
                        bestDir = k;
                    }
                }
                direction[idx] = bestDir < 0 ? HydrologyConstants.FLOW_DIR_SEA : (byte) bestDir;
            }
        }
        return direction;
    }
}
