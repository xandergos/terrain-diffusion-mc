package com.github.xandergos.terraindiffusionmc.hydro;

/**
 * Computes upstream contributing area for each cell (number of upstream cells whose water
 * flows through it).
 *
 * <p>Algorithm : count incoming D8 edges per cell ; cells with zero in-degree are sources
 * (ridge tops, headwaters). Process them in topological order, propagating their accumulated
 * area to their downstream cell. This is the classic O(N) flow accumulation pass.
 *
 * <p>Sea cells (direction {@link HydrologyConstants#FLOW_DIR_SEA}) act as sinks : water reaching
 * them does not propagate further. Still record their accumulated area for diagnostic
 * purposes, but it never feeds into a downstream cell.
 *
 * <p>Returned values are integer cell counts : every cell starts at {@code 1 + boundaryInflow[i]}
 * (it contributes itself plus any flow injected through {@link InboundFlux} from neighboring
 * tiles) and grows as upstream cells route through it.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class FlowAccumulation {

    private FlowAccumulation() {}

    /**
     * Inbound flux contributions from neighboring tiles. Indexed by edge cell.
     *
     * <p>For each cell on the north edge ({@code row = 0}), {@link #fromNorth}[col] is the flux
     * the southern boundary of the north neighbor tile sends into us. Same logic for the three
     * other edges. Pass {@code null} arrays when no neighbor data is available ; the
     * accumulation will then start from 1 per cell as in the standalone case.
     */
    public static final class InboundFlux {
        public final int[] fromNorth;
        public final int[] fromSouth;
        public final int[] fromWest;
        public final int[] fromEast;

        public InboundFlux(int[] fromNorth, int[] fromSouth, int[] fromWest, int[] fromEast) {
            this.fromNorth = fromNorth;
            this.fromSouth = fromSouth;
            this.fromWest = fromWest;
            this.fromEast = fromEast;
        }

        public static final InboundFlux NONE = new InboundFlux(null, null, null, null);
    }

    /** Convenience overload for tiles that don't receive any inbound flux. */
    public static int[] compute(byte[] flowDirection, int width, int height) {
        return compute(flowDirection, width, height, InboundFlux.NONE);
    }

    /**
     * Accumulation pass with optional inbound flux from neighboring tiles. The inbound values
     * are added to the corresponding edge cells before the topological sort starts ; downstream
     * propagation then carries them across the tile.
     */
    public static int[] compute(byte[] flowDirection, int width, int height, InboundFlux inbound) {
        int n = width * height;
        int[] inDegree = new int[n];
        int[] accumulation = new int[n];
        java.util.Arrays.fill(accumulation, 1);

        if (inbound.fromNorth != null) {
            for (int col = 0; col < width; col++) {
                accumulation[col] += inbound.fromNorth[col];
            }
        }
        if (inbound.fromSouth != null) {
            int base = (height - 1) * width;
            for (int col = 0; col < width; col++) {
                accumulation[base + col] += inbound.fromSouth[col];
            }
        }
        if (inbound.fromWest != null) {
            for (int row = 0; row < height; row++) {
                accumulation[row * width] += inbound.fromWest[row];
            }
        }
        if (inbound.fromEast != null) {
            for (int row = 0; row < height; row++) {
                accumulation[row * width + (width - 1)] += inbound.fromEast[row];
            }
        }

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int idx = row * width + col;
                byte dir = flowDirection[idx];
                if (dir == HydrologyConstants.FLOW_DIR_SEA) continue;
                int nr = row + HydrologyConstants.D8_DROW[dir];
                int nc = col + HydrologyConstants.D8_DCOL[dir];
                if (nr < 0 || nr >= height || nc < 0 || nc >= width) continue;
                inDegree[nr * width + nc]++;
            }
        }

        int[] queue = new int[n];
        int head = 0, tail = 0;
        for (int idx = 0; idx < n; idx++) {
            if (inDegree[idx] == 0) queue[tail++] = idx;
        }

        while (head < tail) {
            int idx = queue[head++];
            byte dir = flowDirection[idx];
            if (dir == HydrologyConstants.FLOW_DIR_SEA) continue;
            int row = idx / width;
            int col = idx % width;
            int nr = row + HydrologyConstants.D8_DROW[dir];
            int nc = col + HydrologyConstants.D8_DCOL[dir];
            if (nr < 0 || nr >= height || nc < 0 || nc >= width) continue;
            int nidx = nr * width + nc;
            accumulation[nidx] += accumulation[idx];
            if (--inDegree[nidx] == 0) queue[tail++] = nidx;
        }

        return accumulation;
    }

    /** Result of {@link #extractOutgoingFluxes} : the flow that leaves each edge of the tile. */
    public static final class OutgoingFlux {
        public final int[] toNorth;
        public final int[] toSouth;
        public final int[] toWest;
        public final int[] toEast;

        public OutgoingFlux(int[] toNorth, int[] toSouth, int[] toWest, int[] toEast) {
            this.toNorth = toNorth;
            this.toSouth = toSouth;
            this.toWest = toWest;
            this.toEast = toEast;
        }
    }

    /**
     * Computes how much flow leaves the tile through each edge cell.
     *
     * <p>For an edge cell whose D8 direction crosses outside the tile :
     * <ul>
     *   <li>cardinal-out (e.g. row=0 with dir=N) : full accumulation goes to the corresponding
     *       cardinal neighbor</li>
     *   <li>diagonal-out from a corner (e.g. row=0,col=0 with dir=NW) : half goes north, half
     *       goes west ; total mass is preserved</li>
     *   <li>diagonal-out from a non-corner cell (e.g. row=0,col=5 with dir=NW) : half crosses
     *       the north edge into the north neighbor, the other half stays inside our tile and
     *       is already accounted for by the in-tile accumulation pass ; Don't credit it to
     *       any neighbor to avoid double-counting</li>
     * </ul>
     */
    public static OutgoingFlux extractOutgoingFluxes(byte[] flowDirection, int[] accumulation,
                                                     int width, int height) {
        int[] toNorth = new int[width];
        int[] toSouth = new int[width];
        int[] toWest = new int[height];
        int[] toEast = new int[height];

        // north edge
        for (int col = 0; col < width; col++) {
            byte dir = flowDirection[col];
            if (dir == HydrologyConstants.FLOW_DIR_SEA) continue;
            int drow = HydrologyConstants.D8_DROW[dir];
            int dcol = HydrologyConstants.D8_DCOL[dir];
            if (drow >= 0) continue;
            int flow = accumulation[col];
            if (dcol == 0) {
                toNorth[col] += flow;
            } else {
                int half = flow / 2;
                toNorth[col] += half;
                if (col == 0 && dcol < 0) toWest[0] += flow - half;
                else if (col == width - 1 && dcol > 0) toEast[0] += flow - half;
            }
        }
        // south edge
        for (int col = 0; col < width; col++) {
            int idx = (height - 1) * width + col;
            byte dir = flowDirection[idx];
            if (dir == HydrologyConstants.FLOW_DIR_SEA) continue;
            int drow = HydrologyConstants.D8_DROW[dir];
            int dcol = HydrologyConstants.D8_DCOL[dir];
            if (drow <= 0) continue;
            int flow = accumulation[idx];
            if (dcol == 0) {
                toSouth[col] += flow;
            } else {
                int half = flow / 2;
                toSouth[col] += half;
                if (col == 0 && dcol < 0) toWest[height - 1] += flow - half;
                else if (col == width - 1 && dcol > 0) toEast[height - 1] += flow - half;
            }
        }
        // west edge
        for (int row = 0; row < height; row++) {
            int idx = row * width;
            byte dir = flowDirection[idx];
            if (dir == HydrologyConstants.FLOW_DIR_SEA) continue;
            int drow = HydrologyConstants.D8_DROW[dir];
            int dcol = HydrologyConstants.D8_DCOL[dir];
            if (dcol >= 0) continue;
            // Skip corners : already counted by the row=0/row=height-1 passes above.
            if (row == 0 || row == height - 1) continue;
            int flow = accumulation[idx];
            if (drow == 0) {
                toWest[row] += flow;
            } else {
                toWest[row] += flow / 2;
            }
        }
        // east edge
        for (int row = 0; row < height; row++) {
            int idx = row * width + (width - 1);
            byte dir = flowDirection[idx];
            if (dir == HydrologyConstants.FLOW_DIR_SEA) continue;
            int drow = HydrologyConstants.D8_DROW[dir];
            int dcol = HydrologyConstants.D8_DCOL[dir];
            if (dcol <= 0) continue;
            if (row == 0 || row == height - 1) continue;
            int flow = accumulation[idx];
            if (drow == 0) {
                toEast[row] += flow;
            } else {
                toEast[row] += flow / 2;
            }
        }

        return new OutgoingFlux(toNorth, toSouth, toWest, toEast);
    }
}