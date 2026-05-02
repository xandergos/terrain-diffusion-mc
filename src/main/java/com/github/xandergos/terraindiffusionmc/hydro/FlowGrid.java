package com.github.xandergos.terraindiffusionmc.hydro;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Coarse-resolution hydrological grid covering a multi-tile region.
 * Each cell represents {@code cellSize} blocks on each side.
 *
 * <p>Build via {@link #compute(short[][], int)}.
 */
public final class FlowGrid {

    // D8 directions: N, NE, E, SE, S, SW, W, NW
    static final int[] DX = { 0,  1, 1, 1, 0, -1, -1, -1};
    static final int[] DZ = {-1, -1, 0, 1, 1,  1,  0, -1};

    /** Sentinel: no downhill neighbor (local sink). */
    public static final byte SINK = (byte) 0xFF;

    public final int width;
    public final int height;

    /** D8 flow direction per cell (0-7 or SINK). */
    public final byte[]  flowDir;

    /**
     * Natural-log of flow accumulation (cells). Each cell starts at 1.
     * log1p(acc) stored so values stay ~0-7 for typical regions.
     */
    public final float[] logAcc;

    /** Local slope in m/m (positive = downhill). */
    public final float[] slope;

    private FlowGrid(int width, int height) {
        this.width   = width;
        this.height  = height;
        this.flowDir = new byte [width * height];
        this.logAcc  = new float[width * height];
        this.slope   = new float[width * height];
    }

    /**
     * Build a FlowGrid from a heightmap.
     *
     * @param heightmap  [Z][X] elevation in the same units returned by the pipeline (blocks)
     * @param cellSize   number of blocks per grid cell (must match the downsampling factor)
     */
    public static FlowGrid compute(short[][] heightmap, int cellSize) {
        int H = heightmap.length;
        int W = heightmap[0].length;
        FlowGrid g = new FlowGrid(W, H);

        // 0. Depression filling (Wang & Liu 2006 priority-flood)
        // Eliminates all local sinks so water always drains to the grid boundary.
        // Without this rivers terminate in flat areas / bowls far from the sea.
        float[] filled = fillDepressions(heightmap, W, H);

        // 1. Flow direction (D8 steepest descent on filled DEM)
        for (int z = 0; z < H; z++) {
            for (int x = 0; x < W; x++) {
                float h = filled[z * W + x];
                float maxSlope = Float.NEGATIVE_INFINITY;
                int   bestDir  = -1;

                for (int d = 0; d < 8; d++) {
                    int nx = x + DX[d];
                    int nz = z + DZ[d];
                    if (nx < 0 || nx >= W || nz < 0 || nz >= H) continue;

                    float dist  = (d % 2 == 0) ? cellSize : cellSize * 1.41421356f;
                    float slope = (h - filled[nz * W + nx]) / dist;
                    if (slope > maxSlope) { maxSlope = slope; bestDir = d; }
                }

                int i = z * W + x;
                g.flowDir[i] = (bestDir < 0) ? SINK : (byte) bestDir;
                // Use original (unfilled) heightmap slope for classification
                g.slope  [i] = Math.max(0f, maxSlope);
            }
        }

        // 2. Flow accumulation (topological sort / BFS)
        int   N        = W * H;
        int[] inDegree = new int[N];
        float[] rawAcc = new float[N];
        Arrays.fill(rawAcc, 1f);

        for (int i = 0; i < N; i++) {
            byte dir = g.flowDir[i];
            if (dir == SINK) continue;
            int d  = dir & 0xFF;
            int nx = (i % W) + DX[d];
            int nz = (i / W) + DZ[d];
            if (nx >= 0 && nx < W && nz >= 0 && nz < H)
                inDegree[nz * W + nx]++;
        }

        Queue<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < N; i++)
            if (inDegree[i] == 0) queue.add(i);

        while (!queue.isEmpty()) {
            int i   = queue.poll();
            byte dir = g.flowDir[i];
            if (dir == SINK) continue;
            int d  = dir & 0xFF;
            int nx = (i % W) + DX[d];
            int nz = (i / W) + DZ[d];
            if (nx < 0 || nx >= W || nz < 0 || nz >= H) continue;
            int ni = nz * W + nx;
            rawAcc[ni] += rawAcc[i];
            if (--inDegree[ni] == 0) queue.add(ni);
        }

        for (int i = 0; i < N; i++)
            g.logAcc[i] = (float) Math.log1p(rawAcc[i]);

        return g;
    }

    /**
     * Fills all depressions in the DEM so every cell drains to the boundary.
     *
     * <p>Algorithm : start from all boundary cells (already "open" to the outside)
     * and process cells in ascending elevation order via a min-heap.
     * Interior cells are raised to at least the elevation of the lowest
     * neighbor through which they are reached.
     *
     * <p>O(N log N) time, O(N) space.
     *
     * @return flat float[H*W] array of filled elevations
     */
    private static float[] fillDepressions(short[][] heightmap, int W, int H) {
        int N = W * H;
        float[] elev   = new float[N];
        boolean[] done = new boolean[N];

        for (int z = 0; z < H; z++)
            for (int x = 0; x < W; x++)
                elev[z * W + x] = heightmap[z][x];

        // Min-heap : [elevation, index]
        PriorityQueue<long[]> heap = new PriorityQueue<>(
                (a, b) -> Float.compare(Float.intBitsToFloat((int)(a[0])), Float.intBitsToFloat((int)(b[0])))
        );

        // Seed with all boundary cells
        for (int x = 0; x < W; x++) {
            enqueue(heap, elev, 0,   x, W);
            enqueue(heap, elev, H-1, x, W);
        }
        for (int z = 1; z < H - 1; z++) {
            enqueue(heap, elev, z, 0,   W);
            enqueue(heap, elev, z, W-1, W);
        }

        while (!heap.isEmpty()) {
            long[] entry = heap.poll();
            float  h     = Float.intBitsToFloat((int)(entry[0]));
            int    i     = (int)(entry[1]);
            if (done[i]) continue;
            done[i] = true;

            int x = i % W, z = i / W;
            for (int d = 0; d < 8; d++) {
                int nx = x + DX[d], nz = z + DZ[d];
                if (nx < 0 || nx >= W || nz < 0 || nz >= H) continue;
                int ni = nz * W + nx;
                if (done[ni]) continue;
                // Raise neighbor if it is lower than the current spill level
                // Add a tiny epsilon on flat areas so D8 always finds a downhill direction
                float minH = h + 1e-4f;
                if (elev[ni] < minH) elev[ni] = minH;
                enqueue(heap, elev, nz, nx, W);
            }
        }

        return elev;
    }

    private static void enqueue(PriorityQueue<long[]> heap, float[] elev, int z, int x, int W) {
        int   i = z * W + x;
        float h = elev[i];
        heap.offer(new long[]{ Float.floatToRawIntBits(h), i });
    }
    
    public int index(int x, int z) { return z * width + x; }

    public boolean inBounds(int x, int z) {
        return x >= 0 && x < width && z >= 0 && z < height;
    }
}