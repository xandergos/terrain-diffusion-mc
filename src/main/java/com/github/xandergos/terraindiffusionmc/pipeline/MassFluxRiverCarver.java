package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Local mass-conserving multi-flow river extraction and carving.
 *
 * <p>This is a practical in-game analogue of the Rivix/RiverTools Mass Flux workflow :
 * derive flow from a DEM-like height field, conserve one unit of water mass per grid
 * cell, split that mass among downslope neighbors, accumulate total contributing
 * area and then carve channels where contributing area is high.</p>
 *
 * <p>The original RiverTools implementation is not public API. This implementation is
 * intentionally self-contained, deterministic and chunk-friendly.</p>
 */
public final class MassFluxRiverCarver {
    public static final short RIVER_BIOME_ID = 7;

    /** Keep this equal to the padding used by LocalTerrainProvider. */
    public static final int ANALYSIS_PADDING_BLOCKS = 48;

    private static final int[] DR = {-1,-1,-1, 0,0, 1,1,1};
    private static final int[] DC = {-1, 0, 1,-1,1,-1,0,1};
    private static final float[] DIST = {
            1.41421356237f, 1f, 1.41421356237f,
            1f,             1f,
            1.41421356237f, 1f, 1.41421356237f
    };

    private static final float FLOW_EXPONENT = 1.15f;
    private static final float START_ACCUM_CELLS = 72f;
    private static final float BANKFULL_ACCUM_CELLS = 560f;

    private MassFluxRiverCarver() {
    }

    public static final class Result {
        public final float[] elevation;
        public final boolean[] riverMask;
        public final float[] contributingAreaCells;

        private Result(float[] elevation, boolean[] riverMask, float[] contributingAreaCells) {
            this.elevation = elevation;
            this.riverMask = riverMask;
            this.contributingAreaCells = contributingAreaCells;
        }
    }

    /**
     * Carves only the target window from a larger padded routing DEM.
     *
     * @param routingElev elevation in metres, row-major, dimensions routingH * routingW
     * @param routingH routing DEM height
     * @param routingW routing DEM width
     * @param targetR0 top row of the target window inside routingElev
     * @param targetC0 left column of the target window inside routingElev
     * @param targetH target window height
     * @param targetW target window width
     * @param pixelSizeM horizontal size represented by one grid cell in metres
     */
    public static Result carveTarget(
            float[] routingElev,
            int routingH,
            int routingW,
            int targetR0,
            int targetC0,
            int targetH,
            int targetW,
            float pixelSizeM
    ) {
        if (routingElev == null || routingElev.length != routingH * routingW) {
            throw new IllegalArgumentException("routingElev size does not match routingH * routingW");
        }
        if (targetH <= 0 || targetW <= 0) {
            return new Result(new float[0], new boolean[0], new float[0]);
        }

        float[] hydroSurface = boxBlur(routingElev, routingH, routingW, 2);
        hydroSurface = priorityFloodEpsilon(hydroSurface, routingH, routingW, pixelSizeM);
        float[] areaCells = accumulateMassFlux(hydroSurface, routingElev, routingH, routingW);

        float[] carved = crop(routingElev, routingH, routingW, targetR0, targetC0, targetH, targetW);
        boolean[] riverMask = new boolean[targetH * targetW];
        carveChannels(routingElev, areaCells, routingH, routingW, targetR0, targetC0, targetH, targetW,
                pixelSizeM, carved, riverMask);

        float[] targetArea = crop(areaCells, routingH, routingW, targetR0, targetC0, targetH, targetW);
        return new Result(carved, riverMask, targetArea);
    }

    /**
     * Marks river biomes after the terrain biome classifier has run.
     */
    public static void applyRiverBiomes(short[] biomeIds, boolean[] riverMask) {
        if (biomeIds == null || riverMask == null) return;
        int n = Math.min(biomeIds.length, riverMask.length);
        for (int i = 0; i < n; i++) {
            if (riverMask[i]) biomeIds[i] = RIVER_BIOME_ID;
        }
    }

    private static float[] accumulateMassFlux(float[] hydro, float[] originalElev, int H, int W) {
        int n = H * W;
        float[] accum = new float[n];
        for (int i = 0; i < n; i++) {
            accum[i] = originalElev[i] > 0f ? 1f : 0f;
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble((Integer idx) -> hydro[idx]).reversed());

        int[] dn = new int[8];
        float[] wt = new float[8];

        for (int idx : order) {
            if (accum[idx] <= 0f) continue;
            int r = idx / W;
            int c = idx - r * W;
            if (r == 0 || c == 0 || r == H - 1 || c == W - 1) continue;
            if (originalElev[idx] <= 0f) continue;

            float z = hydro[idx];
            int count = 0;
            float sum = 0f;
            for (int k = 0; k < 8; k++) {
                int rr = r + DR[k];
                int cc = c + DC[k];
                int ni = rr * W + cc;
                float dz = z - hydro[ni];
                if (dz > 1.0e-6f) {
                    float slope = dz / DIST[k];
                    float w = (float) Math.pow(Math.max(1.0e-6f, slope), FLOW_EXPONENT);
                    dn[count] = ni;
                    wt[count] = w;
                    sum += w;
                    count++;
                }
            }

            if (count == 0 || sum <= 0f) continue;
            float mass = accum[idx];
            for (int k = 0; k < count; k++) {
                accum[dn[k]] += mass * wt[k] / sum;
            }
        }
        return accum;
    }

    private static void carveChannels(
            float[] routingElev,
            float[] areaCells,
            int routingH,
            int routingW,
            int targetR0,
            int targetC0,
            int targetH,
            int targetW,
            float pixelSizeM,
            float[] carved,
            boolean[] riverMask
    ) {
        int rMin = Math.max(1, targetR0 - 16);
        int cMin = Math.max(1, targetC0 - 16);
        int rMax = Math.min(routingH - 2, targetR0 + targetH + 16);
        int cMax = Math.min(routingW - 2, targetC0 + targetW + 16);

        for (int r = rMin; r < rMax; r++) {
            for (int c = cMin; c < cMax; c++) {
                int idx = r * routingW + c;
                float acc = areaCells[idx];
                float z = routingElev[idx];
                if (z <= 1f || acc < START_ACCUM_CELLS) continue;

                float mature = saturate((acc - START_ACCUM_CELLS) / (BANKFULL_ACCUM_CELLS - START_ACCUM_CELLS));
                float accScale = (float) Math.pow(acc / START_ACCUM_CELLS, 0.42f);
                float halfWidthCells = Math.min(9.5f, 0.85f + 0.72f * accScale + 5.5f * mature);
                float influenceCells = halfWidthCells + 2.5f;
                float depthM = Math.min(0.9f * pixelSizeM, 1.8f + pixelSizeM * (0.06f + 0.12f * mature) * accScale);

                int rr0 = Math.max(targetR0, (int) Math.floor(r - influenceCells));
                int rr1 = Math.min(targetR0 + targetH - 1, (int) Math.ceil(r + influenceCells));
                int cc0 = Math.max(targetC0, (int) Math.floor(c - influenceCells));
                int cc1 = Math.min(targetC0 + targetW - 1, (int) Math.ceil(c + influenceCells));

                for (int rr = rr0; rr <= rr1; rr++) {
                    float dr = rr - r;
                    for (int cc = cc0; cc <= cc1; cc++) {
                        float dc = cc - c;
                        float d = (float) Math.sqrt(dr * dr + dc * dc);
                        if (d > influenceCells) continue;

                        int outIdx = (rr - targetR0) * targetW + (cc - targetC0);
                        float normalized = d / Math.max(0.001f, halfWidthCells);
                        float cut;
                        if (normalized <= 1f) {
                            float bowl = 1f - normalized * normalized;
                            cut = depthM * (0.35f + 0.65f * bowl * bowl);
                            riverMask[outIdx] = riverMask[outIdx] || normalized < 0.75f;
                        } else {
                            float bank = 1f - ((d - halfWidthCells) / Math.max(0.001f, influenceCells - halfWidthCells));
                            cut = depthM * 0.22f * bank * bank;
                        }
                        carved[outIdx] = Math.min(carved[outIdx], routingElev[idx] - cut);
                    }
                }
            }
        }
    }

    private static float[] priorityFloodEpsilon(float[] elev, int H, int W, float pixelSizeM) {
        float[] filled = elev.clone();
        boolean[] seen = new boolean[H * W];
        float eps = Math.max(0.005f, pixelSizeM * 0.0002f);
        PriorityQueue<Node> pq = new PriorityQueue<>(Comparator.comparingDouble(n -> n.z));

        for (int r = 0; r < H; r++) {
            pushBoundary(r, 0, W, filled, seen, pq);
            pushBoundary(r, W - 1, W, filled, seen, pq);
        }
        for (int c = 1; c < W - 1; c++) {
            pushBoundary(0, c, W, filled, seen, pq);
            pushBoundary(H - 1, c, W, filled, seen, pq);
        }

        while (!pq.isEmpty()) {
            Node node = pq.poll();
            int r = node.idx / W;
            int c = node.idx - r * W;
            for (int k = 0; k < 8; k++) {
                int rr = r + DR[k];
                int cc = c + DC[k];
                if (rr < 0 || cc < 0 || rr >= H || cc >= W) continue;
                int ni = rr * W + cc;
                if (seen[ni]) continue;
                seen[ni] = true;
                if (filled[ni] <= filled[node.idx]) {
                    filled[ni] = filled[node.idx] + eps;
                }
                pq.add(new Node(ni, filled[ni]));
            }
        }
        return filled;
    }

    private static void pushBoundary(int r, int c, int W, float[] filled, boolean[] seen, PriorityQueue<Node> pq) {
        int idx = r * W + c;
        if (seen[idx]) return;
        seen[idx] = true;
        pq.add(new Node(idx, filled[idx]));
    }

    private static final class Node {
        final int idx;
        final float z;
        Node(int idx, float z) {
            this.idx = idx;
            this.z = z;
        }
    }

    private static float[] boxBlur(float[] src, int H, int W, int passes) {
        float[] a = src.clone();
        float[] b = new float[src.length];
        for (int pass = 0; pass < passes; pass++) {
            for (int r = 0; r < H; r++) {
                for (int c = 0; c < W; c++) {
                    float sum = 0f;
                    int count = 0;
                    for (int dr = -1; dr <= 1; dr++) {
                        int rr = r + dr;
                        if (rr < 0 || rr >= H) continue;
                        for (int dc = -1; dc <= 1; dc++) {
                            int cc = c + dc;
                            if (cc < 0 || cc >= W) continue;
                            sum += a[rr * W + cc];
                            count++;
                        }
                    }
                    b[r * W + c] = sum / count;
                }
            }
            float[] tmp = a;
            a = b;
            b = tmp;
        }
        return a;
    }

    private static float[] crop(float[] src, int srcH, int srcW, int r0, int c0, int H, int W) {
        float[] out = new float[H * W];
        for (int r = 0; r < H; r++) {
            int sr = clamp(r0 + r, 0, srcH - 1);
            for (int c = 0; c < W; c++) {
                int sc = clamp(c0 + c, 0, srcW - 1);
                out[r * W + c] = src[sr * srcW + sc];
            }
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float saturate(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
