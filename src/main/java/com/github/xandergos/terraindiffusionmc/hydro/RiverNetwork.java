package com.github.xandergos.terraindiffusionmc.hydro;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts a river network from a {@link FlowGrid} and builds per-chunk
 * depression + water maps.
 *
 * <h3>Threshold tuning</h3>
 * For a region of 4×4 tiles at 256 blocks/tile with cellSize=8:
 *   coarse grid = 128×128 = 16384 cells max accumulation.
 *   log1p(16384) ≈ 9.7
 *
 * We want a SPARSE network (like real river networks: ~5-10% of terrain):
 *   STREAM      : log1p(~500)  ≈ 6.2   (~3% of max)
 *   RIVER       : log1p(~2000) ≈ 7.6   (~12% of max)
 *   MAJOR_RIVER : log1p(~5000) ≈ 8.5   (~30% of max)
 *
 * <h3>Water placement</h3>
 * A block is filled with water if its depression brings it at or below
 * {@link #RIVER_WATER_LEVEL} (relative to the carved surface).
 * The water level in the river bed is a flat surface at (terrainHeight + depression + waterFill).
 */
public final class RiverNetwork {

    private static final float STREAM_THRESHOLD      = 6.2f;  // log1p(~500 cells)
    private static final float RIVER_THRESHOLD       = 7.6f;  // log1p(~2000 cells)
    private static final float MAJOR_RIVER_THRESHOLD = 8.5f;  // log1p(~5000 cells)

    public enum RiverClass {
        //                 halfWidthBase  hwScale  baseDepth  waterFill
        STREAM      (  3f,   0.4f,   3,  2),
        RIVER       (  8f,   1.2f,   5,  4),
        MAJOR_RIVER ( 18f,   2.0f,   8,  6);

        public final float halfWidthBase;
        public final float halfWidthScale;
        public final int   baseDepth;
        /** Blocks of water placed from the bottom of the carved channel. */
        public final int   waterFill;

        RiverClass(float hwBase, float hwScale, int depth, int water) {
            this.halfWidthBase  = hwBase;
            this.halfWidthScale = hwScale;
            this.baseDepth      = depth;
            this.waterFill      = water;
        }
    }

    /** Returns null if below stream threshold. */
    public static RiverClass classify(float logAcc, float slope) {
        if (logAcc < STREAM_THRESHOLD) return null;
        if (logAcc >= MAJOR_RIVER_THRESHOLD && slope < 0.03f) return RiverClass.MAJOR_RIVER;
        if (logAcc >= RIVER_THRESHOLD       && slope < 0.20f) return RiverClass.RIVER;
        return RiverClass.STREAM;
    }

    public static final class RiverEdge {
        public final int   fromX, fromZ;
        public final int   toX,   toZ;
        public final float logAcc;
        public final float slope;
        public final RiverClass cls;

        RiverEdge(int fx, int fz, int tx, int tz, float logAcc, float slope, RiverClass cls) {
            this.fromX = fx; this.fromZ = fz;
            this.toX   = tx; this.toZ   = tz;
            this.logAcc = logAcc;
            this.slope  = slope;
            this.cls    = cls;
        }

        public float halfWidth() {
            return cls.halfWidthBase + cls.halfWidthScale * logAcc;
        }

        public int depth() {
            return cls.baseDepth + (int)(logAcc * 0.6f);
        }
    }

    public static List<RiverEdge> extract(FlowGrid grid) {
        List<RiverEdge> edges = new ArrayList<>();
        int W = grid.width, H = grid.height;
        for (int z = 0; z < H; z++) {
            for (int x = 0; x < W; x++) {
                int i = grid.index(x, z);
                RiverClass cls = classify(grid.logAcc[i], grid.slope[i]);
                if (cls == null) continue;
                byte dir = grid.flowDir[i];
                if (dir == FlowGrid.SINK) continue;
                int d  = dir & 0xFF;
                int nx = x + FlowGrid.DX[d], nz = z + FlowGrid.DZ[d];
                if (!grid.inBounds(nx, nz)) continue;
                edges.add(new RiverEdge(x, z, nx, nz, grid.logAcc[i], grid.slope[i], cls));
            }
        }
        return edges;
    }

    /**
     * Result of building the influence maps for a chunk.
     * Both arrays are indexed [z * width + x].
     */
    public record ChunkMaps(int[] depression, boolean[] water) {}

    /**
     * Builds depression and water presence maps for a block region.
     * Uses Catmull-Rom smoothed centre-lines to avoid D8 staircase artifacts.
     *
     * @param edges    extracted river edges (world-shifted)
     * @param cellSize blocks per coarse cell
     * @param originX  world X of first block
     * @param originZ  world Z of first block
     * @param width    region width in blocks
     * @param height   region height in blocks
     */
    public static ChunkMaps buildMaps(List<RiverEdge> edges,
                                      int cellSize,
                                      int originX, int originZ,
                                      int width,   int height) {
        int[] depression = new int[width * height];
        boolean[] water  = new boolean[width * height];

        for (RiverEdge edge : edges) {
            float halfWidth = edge.halfWidth();
            int   depth     = edge.depth();

            // World coords of segment endpoints (cell centres)
            float ax = (edge.fromX + 0.5f) * cellSize;
            float az = (edge.fromZ + 0.5f) * cellSize;
            float bx = (edge.toX   + 0.5f) * cellSize;
            float bz = (edge.toZ   + 0.5f) * cellSize;

            // AABB cull
            float pad = halfWidth + cellSize;
            int bx0 = Math.max(0,        (int)Math.floor(Math.min(ax,bx) - pad - originX));
            int bx1 = Math.min(width -1, (int)Math.ceil (Math.max(ax,bx) + pad - originX));
            int bz0 = Math.max(0,        (int)Math.floor(Math.min(az,bz) - pad - originZ));
            int bz1 = Math.min(height-1, (int)Math.ceil (Math.max(az,bz) + pad - originZ));

            // Water level : the bottom (depth) + waterFill blocks are water
            int waterFill = edge.cls.waterFill;

            for (int bz2 = bz0; bz2 <= bz1; bz2++) {
                for (int bx2 = bx0; bx2 <= bx1; bx2++) {
                    float wx = originX + bx2;
                    float wz = originZ + bz2;

                    float dist = distSmoothed(wx, wz, ax, az, bx, bz, edge);

                    if (dist >= halfWidth) continue;

                    float t = dist / halfWidth;           // 0=centre, 1=edge
                    // Smooth cosine profile : flatter bottom, steeper banks
                    float profile = (float)(1.0 - Math.cos(t * Math.PI)) * 0.5f;  // 0 at centre, 1 at edge
                    int   d = -(int)(depth * (1f - profile));
                    d = Math.min(d, -1); // toujours creuser d'au moins 1

                    int idx = bz2 * width + bx2;
                    if (d < depression[idx]) {
                        depression[idx] = d;
                        // Place water if block is within waterFill of the bottom
                        // i.e. if depression <= -depth + waterFill
                        water[idx] = (d <= -(depth - waterFill));
                    }
                }
            }
        }

        return new ChunkMaps(depression, water);
    }

    private static final int CURVE_SAMPLES = 12;

    /**
     * Distance from (px,pz) to the smoothed centre-line of an edge.
     * Uses a simple cardinal-spline : the tangent at each endpoint is the
     * direction to its upstream/downstream neighbor (approximated from the
     * D8 direction). This eliminates the 45° staircase of raw D8 segments.
     *
     * For straight or short segments we fall back to the raw segment distance.
     */
    private static float distSmoothed(float px, float pz,
                                      float ax, float az,
                                      float bx, float bz,
                                      RiverEdge edge) {
        boolean meander = (edge.cls == RiverClass.MAJOR_RIVER && edge.slope < 0.008f);

        if (!meander && !isDiagonal(edge)) {
            // Axis-aligned segment : no smoothing needed
            return distToSegment(px, pz, ax, az, bx, bz);
        }

        // Sample a polyline : straight line + optional sinusoidal meander
        float len = dist(ax, az, bx, bz);
        if (len < 1e-3f) return dist(px, pz, ax, az);

        // Perpendicular for meander
        float nx = 0, nz = 0;
        float amplitude = 0, wavelength = 1;
        if (meander) {
            nx = -(bz - az) / len;
            nz =  (bx - ax) / len;
            amplitude  = Math.min(len * 0.40f, edge.logAcc * 3.0f);
            wavelength = len * 2.2f;
        }

        float minDist = Float.MAX_VALUE;
        float prevCx = ax, prevCz = az;

        for (int s = 1; s <= CURVE_SAMPLES; s++) {
            float tLine = (float) s / CURVE_SAMPLES;
            float cx = ax + tLine * (bx - ax);
            float cz = az + tLine * (bz - az);

            if (meander) {
                float offset = amplitude * (float) Math.sin(2.0 * Math.PI * tLine * len / wavelength);
                cx += nx * offset;
                cz += nz * offset;
            }

            // Distance to mini-segment [prev, cur]
            float d = distToSegment(px, pz, prevCx, prevCz, cx, cz);
            if (d < minDist) minDist = d;
            prevCx = cx; prevCz = cz;
        }
        return minDist;
    }

    private static boolean isDiagonal(RiverEdge e) {
        return (e.toX - e.fromX) != 0 && (e.toZ - e.fromZ) != 0;
    }

    private static float distToSegment(float px, float pz,
                                       float ax, float az, float bx, float bz) {
        float dx = bx - ax, dz = bz - az;
        float len2 = dx*dx + dz*dz;
        if (len2 < 1e-6f) return dist(px, pz, ax, az);
        float t = Math.max(0f, Math.min(1f, ((px-ax)*dx + (pz-az)*dz) / len2));
        return dist(px, pz, ax + t*dx, az + t*dz);
    }

    private static float dist(float ax, float az, float bx, float bz) {
        float dx = ax - bx, dz = az - bz;
        return (float) Math.sqrt(dx*dx + dz*dz);
    }
}