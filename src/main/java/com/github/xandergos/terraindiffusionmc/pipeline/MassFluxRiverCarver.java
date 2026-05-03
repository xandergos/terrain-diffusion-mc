package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Local, mass-conserving river extraction and centerline-based carving.
 *
 * <p>The routing pass keeps the previous RiverTools/Rivix-inspired mass-flux idea for
 * contributing area, but the carving pass no longer treats every high-accumulation
 * raster cell as a wide river. It first extracts a one-cell-wide primary channel
 * centerline from a D8 tree, computes a Shreve-like magnitude on that tree, combines
 * it with mass-flux accumulation, and then rasterizes calibrated channel cross-sections
 * around the resulting polyline segments.</p>
 *
 * <p>The original RiverTools implementation is not public API. This implementation is
 * self-contained, deterministic, and chunk-friendly.</p>
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

    // Centerline initiation. D8 accumulation gives crisp lines; mass-flux accumulation sizes them.
    private static final float START_D8_ACCUM_CELLS = 96f;
    private static final float START_MASS_ACCUM_CELLS = 58f;
    private static final float BANKFULL_ACCUM_CELLS = 900f;

    private static final float MIN_CHANNEL_SLOPE = 0.00008f;
    private static final float MIN_DROP_BLOCKS = 0.045f;

    private MassFluxRiverCarver() {
    }

    public static final class Result {
        public final float[] elevation;
        /** Water-channel mask only. Carved banks are deliberately not marked as river. */
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
     * @param pixelSizeM horizontal size represented by one grid cell, in metres
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

        Integer[] order = sortedHighToLow(hydroSurface);
        int[] primaryDown = computePrimaryDownstream(hydroSurface, routingElev, routingH, routingW, pixelSizeM);
        float[] massAreaCells = accumulateMassFlux(hydroSurface, routingElev, routingH, routingW, order);
        float[] d8AreaCells = accumulateD8(primaryDown, routingElev, routingH, routingW, order);
        boolean[] centerline = extractCenterline(primaryDown, hydroSurface, routingElev, massAreaCells, d8AreaCells,
                routingH, routingW, pixelSizeM);
        float[] shreve = computeShreveMagnitude(primaryDown, centerline, order);

        float[] carved = crop(routingElev, routingH, routingW, targetR0, targetC0, targetH, targetW);
        boolean[] riverMask = new boolean[targetH * targetW];
        carvePolylineChannels(routingElev, hydroSurface, primaryDown, centerline, massAreaCells, d8AreaCells, shreve,
                routingH, routingW, targetR0, targetC0, targetH, targetW, pixelSizeM, carved, riverMask);

        float[] targetArea = crop(massAreaCells, routingH, routingW, targetR0, targetC0, targetH, targetW);
        return new Result(carved, riverMask, targetArea);
    }

    /** Marks river biomes after the terrain biome classifier has run. */
    public static void applyRiverBiomes(short[] biomeIds, boolean[] riverMask) {
        if (biomeIds == null || riverMask == null) return;
        int n = Math.min(biomeIds.length, riverMask.length);
        for (int i = 0; i < n; i++) {
            if (riverMask[i]) biomeIds[i] = RIVER_BIOME_ID;
        }
    }

    private static Integer[] sortedHighToLow(float[] hydro) {
        Integer[] order = new Integer[hydro.length];
        for (int i = 0; i < hydro.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble((Integer idx) -> hydro[idx]).reversed());
        return order;
    }

    private static int[] computePrimaryDownstream(float[] hydro, float[] originalElev, int H, int W, float pixelSizeM) {
        int[] down = new int[H * W];
        Arrays.fill(down, -1);
        for (int r = 1; r < H - 1; r++) {
            for (int c = 1; c < W - 1; c++) {
                int idx = r * W + c;
                if (originalElev[idx] <= 0f) continue;
                float z = hydro[idx];
                float bestSlope = 0f;
                int best = -1;
                for (int k = 0; k < 8; k++) {
                    int rr = r + DR[k];
                    int cc = c + DC[k];
                    int ni = rr * W + cc;
                    float dz = z - hydro[ni];
                    if (dz <= 1.0e-6f) continue;
                    float slope = dz / (DIST[k] * pixelSizeM);
                    if (slope > bestSlope) {
                        bestSlope = slope;
                        best = ni;
                    }
                }
                down[idx] = best;
            }
        }
        return down;
    }

    private static float[] accumulateD8(int[] primaryDown, float[] originalElev, int H, int W, Integer[] order) {
        int n = H * W;
        float[] accum = new float[n];
        for (int i = 0; i < n; i++) {
            accum[i] = originalElev[i] > 0f ? 1f : 0f;
        }
        for (int idx : order) {
            int dn = primaryDown[idx];
            if (dn >= 0 && accum[idx] > 0f) {
                accum[dn] += accum[idx];
            }
        }
        return accum;
    }

    private static float[] accumulateMassFlux(float[] hydro, float[] originalElev, int H, int W, Integer[] order) {
        int n = H * W;
        float[] accum = new float[n];
        for (int i = 0; i < n; i++) {
            accum[i] = originalElev[i] > 0f ? 1f : 0f;
        }

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

    private static boolean[] extractCenterline(
            int[] primaryDown,
            float[] hydro,
            float[] originalElev,
            float[] massArea,
            float[] d8Area,
            int H,
            int W,
            float pixelSizeM
    ) {
        boolean[] out = new boolean[H * W];
        for (int r = 1; r < H - 1; r++) {
            for (int c = 1; c < W - 1; c++) {
                int idx = r * W + c;
                int dn = primaryDown[idx];
                if (dn < 0 || originalElev[idx] <= 1f) continue;
                float dz = Math.max(0f, hydro[idx] - hydro[dn]);
                float slope = dz / (distanceBetween(idx, dn, W) * pixelSizeM);

                // Crisp one-cell channels come from D8 accumulation; mass area prevents tiny noisy paths.
                if (d8Area[idx] >= START_D8_ACCUM_CELLS && massArea[idx] >= START_MASS_ACCUM_CELLS) {
                    if (slope >= MIN_CHANNEL_SLOPE || d8Area[idx] >= START_D8_ACCUM_CELLS * 2.0f) {
                        out[idx] = true;
                    }
                }
            }
        }
        return out;
    }

    private static float[] computeShreveMagnitude(int[] primaryDown, boolean[] centerline, Integer[] order) {
        float[] shreve = new float[centerline.length];
        for (int idx : order) {
            if (!centerline[idx]) continue;
            if (shreve[idx] <= 0f) shreve[idx] = 1f;
            int dn = primaryDown[idx];
            if (dn >= 0 && centerline[dn]) {
                shreve[dn] += shreve[idx];
            }
        }
        return shreve;
    }

    private static void carvePolylineChannels(
            float[] routingElev,
            float[] hydro,
            int[] primaryDown,
            boolean[] centerline,
            float[] massArea,
            float[] d8Area,
            float[] shreve,
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
        float minDropM = pixelSizeM * MIN_DROP_BLOCKS;
        float[] bed = buildMonotoneBed(routingElev, primaryDown, centerline, massArea, d8Area, shreve,
                routingH, routingW, pixelSizeM, minDropM);

        int rMin = Math.max(1, targetR0 - ANALYSIS_PADDING_BLOCKS);
        int cMin = Math.max(1, targetC0 - ANALYSIS_PADDING_BLOCKS);
        int rMax = Math.min(routingH - 2, targetR0 + targetH + ANALYSIS_PADDING_BLOCKS);
        int cMax = Math.min(routingW - 2, targetC0 + targetW + ANALYSIS_PADDING_BLOCKS);

        for (int r = rMin; r < rMax; r++) {
            for (int c = cMin; c < cMax; c++) {
                int idx = r * routingW + c;
                if (!centerline[idx]) continue;

                int dn = primaryDown[idx];
                if (dn >= 0 && centerline[dn]) {
                    rasterizeSegment(idx, dn, routingElev, bed, massArea, d8Area, shreve,
                            routingW, targetR0, targetC0, targetH, targetW, pixelSizeM, carved, riverMask);
                } else {
                    rasterizePoint(idx, routingElev, bed, massArea, d8Area, shreve,
                            routingW, targetR0, targetC0, targetH, targetW, pixelSizeM, carved, riverMask);
                }
            }
        }
    }

    private static float[] buildMonotoneBed(
            float[] routingElev,
            int[] primaryDown,
            boolean[] centerline,
            float[] massArea,
            float[] d8Area,
            float[] shreve,
            int H,
            int W,
            float pixelSizeM,
            float minDropM
    ) {
        int n = routingElev.length;
        float[] bed = new float[n];
        Arrays.fill(bed, Float.NaN);
        for (int idx = 0; idx < n; idx++) {
            if (!centerline[idx]) continue;
            ChannelShape shape = shapeAt(idx, massArea, d8Area, shreve, pixelSizeM);
            bed[idx] = routingElev[idx] - shape.depthM;
        }

        Integer[] lowToHigh = new Integer[n];
        for (int i = 0; i < n; i++) lowToHigh[i] = i;
        Arrays.sort(lowToHigh, Comparator.comparingDouble((Integer idx) -> routingElev[idx]));

        // Reduce stair-step artifacts: upstream beds should not be lower than their downstream parent.
        for (int pass = 0; pass < 2; pass++) {
            for (int idx : lowToHigh) {
                if (!centerline[idx]) continue;
                int dn = primaryDown[idx];
                if (dn >= 0 && centerline[dn] && !Float.isNaN(bed[dn])) {
                    bed[idx] = Math.max(bed[idx], bed[dn] + minDropM * distanceBetween(idx, dn, W));
                }
            }
        }
        return bed;
    }

    private static void rasterizeSegment(
            int a,
            int b,
            float[] routingElev,
            float[] bed,
            float[] massArea,
            float[] d8Area,
            float[] shreve,
            int routingW,
            int targetR0,
            int targetC0,
            int targetH,
            int targetW,
            float pixelSizeM,
            float[] carved,
            boolean[] riverMask
    ) {
        int ar = a / routingW, ac = a - ar * routingW;
        int br = b / routingW, bc = b - br * routingW;
        ChannelShape sa = shapeAt(a, massArea, d8Area, shreve, pixelSizeM);
        ChannelShape sb = shapeAt(b, massArea, d8Area, shreve, pixelSizeM);
        float bankRadius = Math.max(sa.bankRadiusCells, sb.bankRadiusCells);
        int rr0 = Math.max(targetR0, (int) Math.floor(Math.min(ar, br) - bankRadius - 1));
        int rr1 = Math.min(targetR0 + targetH - 1, (int) Math.ceil(Math.max(ar, br) + bankRadius + 1));
        int cc0 = Math.max(targetC0, (int) Math.floor(Math.min(ac, bc) - bankRadius - 1));
        int cc1 = Math.min(targetC0 + targetW - 1, (int) Math.ceil(Math.max(ac, bc) + bankRadius + 1));

        float vx = bc - ac;
        float vy = br - ar;
        float len2 = vx * vx + vy * vy;
        for (int rr = rr0; rr <= rr1; rr++) {
            for (int cc = cc0; cc <= cc1; cc++) {
                float t = len2 <= 1.0e-6f ? 0f : (((cc - ac) * vx + (rr - ar) * vy) / len2);
                t = saturate(t);
                float pr = ar + t * vy;
                float pc = ac + t * vx;
                float dr = rr - pr;
                float dc = cc - pc;
                float dist = (float) Math.sqrt(dr * dr + dc * dc);

                ChannelShape s = interpolate(sa, sb, t);
                if (dist > s.bankRadiusCells) continue;
                float centerBed = lerp(safeBed(bed[a], routingElev[a] - sa.depthM), safeBed(bed[b], routingElev[b] - sb.depthM), t);
                applyCrossSection(dist, s, centerBed, rr, cc, targetR0, targetC0, targetW, carved, riverMask);
            }
        }
    }

    private static void rasterizePoint(
            int a,
            float[] routingElev,
            float[] bed,
            float[] massArea,
            float[] d8Area,
            float[] shreve,
            int routingW,
            int targetR0,
            int targetC0,
            int targetH,
            int targetW,
            float pixelSizeM,
            float[] carved,
            boolean[] riverMask
    ) {
        int ar = a / routingW, ac = a - ar * routingW;
        ChannelShape s = shapeAt(a, massArea, d8Area, shreve, pixelSizeM);
        int rr0 = Math.max(targetR0, (int) Math.floor(ar - s.bankRadiusCells - 1));
        int rr1 = Math.min(targetR0 + targetH - 1, (int) Math.ceil(ar + s.bankRadiusCells + 1));
        int cc0 = Math.max(targetC0, (int) Math.floor(ac - s.bankRadiusCells - 1));
        int cc1 = Math.min(targetC0 + targetW - 1, (int) Math.ceil(ac + s.bankRadiusCells + 1));
        float centerBed = safeBed(bed[a], routingElev[a] - s.depthM);
        for (int rr = rr0; rr <= rr1; rr++) {
            for (int cc = cc0; cc <= cc1; cc++) {
                float dr = rr - ar;
                float dc = cc - ac;
                float dist = (float) Math.sqrt(dr * dr + dc * dc);
                if (dist > s.bankRadiusCells) continue;
                applyCrossSection(dist, s, centerBed, rr, cc, targetR0, targetC0, targetW, carved, riverMask);
            }
        }
    }

    private static void applyCrossSection(
            float dist,
            ChannelShape s,
            float centerBed,
            int rr,
            int cc,
            int targetR0,
            int targetC0,
            int targetW,
            float[] carved,
            boolean[] riverMask
    ) {
        int outIdx = (rr - targetR0) * targetW + (cc - targetC0);
        float target;
        if (dist <= s.waterRadiusCells) {
            // Flat-ish low-flow bed, so water is narrow and continuous.
            float t = dist / Math.max(0.001f, s.waterRadiusCells);
            target = centerBed + s.depthM * 0.10f * t * t;
            riverMask[outIdx] = true;
        } else {
            // Sloped bank, carved but not filled with water.
            float t = (dist - s.waterRadiusCells) / Math.max(0.001f, s.bankRadiusCells - s.waterRadiusCells);
            float bankRise = s.depthM * (0.18f + 0.82f * smoothstep(t));
            target = centerBed + bankRise;
        }
        carved[outIdx] = Math.min(carved[outIdx], target);
    }

    private static ChannelShape shapeAt(int idx, float[] massArea, float[] d8Area, float[] shreve, float pixelSizeM) {
        float areaNorm = Math.max(1f, massArea[idx] / START_MASS_ACCUM_CELLS);
        float d8Norm = Math.max(1f, d8Area[idx] / START_D8_ACCUM_CELLS);
        float shreveNorm = Math.max(1f, shreve[idx]);
        float mature = saturate((massArea[idx] - START_MASS_ACCUM_CELLS) / (BANKFULL_ACCUM_CELLS - START_MASS_ACCUM_CELLS));

        // Shreve magnitude handles tributary hierarchy; accumulation keeps width continuous inside long trunks.
        float flowPriority = 0.62f * (float) Math.pow(areaNorm, 0.34f)
                + 0.24f * (float) Math.pow(shreveNorm, 0.42f)
                + 0.14f * (float) Math.pow(d8Norm, 0.25f);

        float waterRadiusCells = clamp(0.48f + 0.28f * flowPriority + 1.15f * mature, 0.62f, 4.9f);
        float bankRadiusCells = clamp(waterRadiusCells * (1.72f + 0.18f * mature), waterRadiusCells + 0.8f, 8.4f);

        float depthBlocks = clamp(0.92f + 0.34f * (float) Math.pow(areaNorm, 0.27f)
                        + 0.23f * (float) Math.pow(shreveNorm, 0.35f)
                        + 1.15f * mature,
                1.05f, 5.8f);
        float depthM = depthBlocks * pixelSizeM;
        return new ChannelShape(waterRadiusCells, bankRadiusCells, depthM);
    }

    private static ChannelShape interpolate(ChannelShape a, ChannelShape b, float t) {
        return new ChannelShape(
                lerp(a.waterRadiusCells, b.waterRadiusCells, t),
                lerp(a.bankRadiusCells, b.bankRadiusCells, t),
                lerp(a.depthM, b.depthM, t)
        );
    }

    private record ChannelShape(float waterRadiusCells, float bankRadiusCells, float depthM) {}

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

    private static float distanceBetween(int a, int b, int W) {
        int ar = a / W, ac = a - ar * W;
        int br = b / W, bc = b - br * W;
        int dr = Math.abs(ar - br);
        int dc = Math.abs(ac - bc);
        return (dr == 1 && dc == 1) ? 1.41421356237f : 1f;
    }

    private static float safeBed(float v, float fallback) {
        return Float.isNaN(v) ? fallback : v;
    }

    private static float smoothstep(float t) {
        t = saturate(t);
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float saturate(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
