package com.github.xandergos.terraindiffusionmc.hydro;

/**
 * Turns the raw hydrological data of a {@link HydrologyTile} into a per-cell
 * {@link HydrologicalFeature} grid.
 *
 * <p>Classification rules (current set, easy to extend) :
 * <ul>
 *   <li>{@link HydrologicalFeature#NONE} : default land cell, no significant flow or sea cell
 *       outside any river plume</li>
 *   <li>{@link HydrologicalFeature#STREAM} : upstream cells with
 *       {@code accumulation >= STREAM_THRESHOLD}</li>
 *   <li>{@link HydrologicalFeature#RIVER} : cells with
 *       {@code accumulation >= RIVER_THRESHOLD}</li>
 *   <li>{@link HydrologicalFeature#TRUNK_RIVER} : cells with
 *       {@code accumulation >= TRUNK_RIVER_THRESHOLD}</li>
 *   <li>{@link HydrologicalFeature#RIVER_MOUTH} : sea cells within a radius proportional to
 *       {@code sqrt(accum)} around any inland cell whose flow direction takes it directly into
 *       the sea</li>
 * </ul>
 *
 * <p>Adding a new feature later (delta, lake, meander, ect...) means adding a branch here and a
 * constant in {@link HydrologicalFeature}. The renderer and any future block placer read the
 * resulting byte grid uniformly.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyClassifier {

    /** Minimum upstream cell count for a cell to register as a stream. */
    public static final int STREAM_THRESHOLD = 50;

    /** Minimum upstream cell count for a cell to register as a river. */
    public static final int RIVER_THRESHOLD = 1000;

    /** Minimum upstream cell count for a cell to register as a trunk river. */
    public static final int TRUNK_RIVER_THRESHOLD = 20_000;

    /** Divisor applied to {@code sqrt(accumulation)} to derive the mouth plume radius (cells). */
    private static final double MOUTH_RADIUS_DIVISOR = 30.0;

    /** Hard cap on mouth plume radius so big rivers don't paint half the sea. */
    private static final int MOUTH_RADIUS_MAX = 24;

    private HydrologyClassifier() {}

    /**
     * Classifies every cell of the tile into a {@link HydrologicalFeature}.
     *
     * @return a row-major byte array of length {@code width * height} ; each entry is a feature
     *         {@code ordinal()}
     */
    public static byte[] classify(byte[] flowDirection, int[] flowAccumulation,
                                  short[][] heightmap, int width, int height) {
        byte[] features = new byte[width * height];

        // Pass 1 : stream / river / trunk river based on accumulation thresholds.
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int idx = row * width + col;
                if (flowDirection[idx] == HydrologyConstants.FLOW_DIR_SEA) continue;
                int accum = flowAccumulation[idx];
                if (accum >= TRUNK_RIVER_THRESHOLD) {
                    features[idx] = (byte) HydrologicalFeature.TRUNK_RIVER.ordinal();
                } else if (accum >= RIVER_THRESHOLD) {
                    features[idx] = (byte) HydrologicalFeature.RIVER.ordinal();
                } else if (accum >= STREAM_THRESHOLD) {
                    features[idx] = (byte) HydrologicalFeature.STREAM.ordinal();
                }
            }
        }

        // Pass 2 : river mouths. For each river cell that flows directly into a sea neighbor
        // and paint a circular plume on the surrounding sea cells. Plume radius scales as
        // sqrt(accum) / 30 so streams produce no plume, rivers a small one, trunk rivers a
        // visible delta-sized one.
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int idx = row * width + col;
                byte feat = features[idx];
                // Only river-tier cells produce a mouth.
                if (feat != (byte) HydrologicalFeature.RIVER.ordinal()
                        && feat != (byte) HydrologicalFeature.TRUNK_RIVER.ordinal()) continue;

                byte dir = flowDirection[idx];
                if (dir == HydrologyConstants.FLOW_DIR_SEA) continue;
                int nr = row + HydrologyConstants.D8_DROW[dir];
                int nc = col + HydrologyConstants.D8_DCOL[dir];
                if (nr < 0 || nr >= height || nc < 0 || nc >= width) continue;
                int nidx = nr * width + nc;
                if (flowDirection[nidx] != HydrologyConstants.FLOW_DIR_SEA) continue;
                // Downstream is sea : paint a plume centred on the sea cell.
                int radius = mouthRadius(flowAccumulation[idx]);
                paintMouthPlume(features, flowDirection, nr, nc, radius, width, height);
            }
        }

        return features;
    }

    private static int mouthRadius(int accumulation) {
        int r = (int) Math.round(Math.sqrt(Math.max(1, accumulation)) / MOUTH_RADIUS_DIVISOR);
        if (r < 1) r = 1;
        if (r > MOUTH_RADIUS_MAX) r = MOUTH_RADIUS_MAX;
        return r;
    }

    /**
     * Paints {@link HydrologicalFeature#RIVER_MOUTH} on every sea cell within {@code radius} of
     * {@code (centreRow, centreCol)} that isn't already painted with a higher-priority feature.
     */
    private static void paintMouthPlume(byte[] features, byte[] flowDirection,
                                        int centreRow, int centreCol, int radius,
                                        int width, int height) {
        byte mouthByte = (byte) HydrologicalFeature.RIVER_MOUTH.ordinal();
        int rowMin = Math.max(0, centreRow - radius);
        int rowMax = Math.min(height - 1, centreRow + radius);
        int colMin = Math.max(0, centreCol - radius);
        int colMax = Math.min(width - 1, centreCol + radius);
        int radiusSq = radius * radius;
        for (int row = rowMin; row <= rowMax; row++) {
            int dr = row - centreRow;
            for (int col = colMin; col <= colMax; col++) {
                int dc = col - centreCol;
                if (dr * dr + dc * dc > radiusSq) continue;
                int idx = row * width + col;
                // Only overpaint sea cells. River cells stay rivers.
                if (flowDirection[idx] != HydrologyConstants.FLOW_DIR_SEA) continue;
                // Don't overwrite an existing mouth (preserves NONE -> MOUTH but doesn't reset).
                if (features[idx] == 0 /* NONE */) {
                    features[idx] = mouthByte;
                }
            }
        }
    }
}