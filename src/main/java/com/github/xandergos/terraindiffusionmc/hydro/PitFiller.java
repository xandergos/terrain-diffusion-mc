package com.github.xandergos.terraindiffusionmc.hydro;

import java.util.PriorityQueue;

/**
 * Fills closed depressions in a heightmap so every land cell has a monotonically descending path
 * to a boundary cell or to the sea.
 *
 * <p>Implements the Priority-Flood algorithm (Barnes, Lehman, Mulla - 2014) :
 * <ol>
 *   <li>seed a priority queue with all boundary cells AND every land cell that already sits at or
 *       below sea level (those are sinks)</li>
 *   <li>pop the lowest-elevation cell and visit each neighbor : if not yet visited, raise its
 *       elevation to {@code max(neighbourElev, popped.spillElev)} and push it with that spill
 *       elevation as priority</li>
 * </ol>
 *
 * <p>The result is a heightmap where every cell either drains to a boundary or to the sea. Pits
 * (closed basins surrounded by higher ground entirely inside the tile) get raised to the
 * elevation of their lowest spillover point, which is the standard hydrological flat-removal
 * trick ; flow direction can then be computed unambiguously.
 */
public final class PitFiller {

    private PitFiller() {}

    /**
     * Returns a copy of {@code heightmap} with every depression filled to its spill elevation.
     * Cells at or below {@link HydrologyConstants#SEA_LEVEL_Y} are left unchanged and act as
     * additional starting sinks alongside the tile boundary.
     *
     * @param heightmap row-major elevations in Minecraft Y (block coordinates)
     * @param width     number of columns
     * @param height    number of rows
     * @return a new {@code short[height][width]} with depressions filled
     */
    public static short[][] fill(short[][] heightmap, int width, int height) {
        short[][] filled = new short[height][width];
        for (int row = 0; row < height; row++) {
            System.arraycopy(heightmap[row], 0, filled[row], 0, width);
        }

        boolean[] visited = new boolean[width * height];
        // PriorityQueue ordered by spill elevation ascending. Each entry packs (elev, row and col)
        // into a long for allocation-free sorting : 16 bits elev + 16 bits row + 16 bits col fits
        // any reasonable Minecraft tile.
        PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));

        // Seed boundary cells.
        for (int col = 0; col < width; col++) {
            seed(queue, visited, filled, 0, col, width);
            seed(queue, visited, filled, height - 1, col, width);
        }
        for (int row = 1; row < height - 1; row++) {
            seed(queue, visited, filled, row, 0, width);
            seed(queue, visited, filled, row, width - 1, width);
        }

        // Seed every cell at or below sea level — these are interior sinks.
        for (int row = 1; row < height - 1; row++) {
            for (int col = 1; col < width - 1; col++) {
                if (filled[row][col] <= HydrologyConstants.SEA_LEVEL_Y) {
                    seed(queue, visited, filled, row, col, width);
                }
            }
        }

        // Process the queue. Each pop yields the lowest unprocessed spillover ; visit its
        // 8-neighbors and raise them to at least the spill elevation.
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int spillElev = (int) entry[0];
            int row = (int) entry[1];
            int col = (int) entry[2];

            for (int k = 0; k < 8; k++) {
                int nr = row + HydrologyConstants.D8_DROW[k];
                int nc = col + HydrologyConstants.D8_DCOL[k];
                if (nr < 0 || nr >= height || nc < 0 || nc >= width) continue;
                int nidx = nr * width + nc;
                if (visited[nidx]) continue;

                int neighbourElev = filled[nr][nc];
                int raised = Math.max(neighbourElev, spillElev);
                if (raised != neighbourElev) {
                    filled[nr][nc] = (short) raised;
                }
                visited[nidx] = true;
                queue.add(new long[]{ raised, nr, nc });
            }
        }
        return filled;
    }

    private static void seed(PriorityQueue<long[]> queue, boolean[] visited,
                             short[][] filled, int row, int col, int width) {
        int idx = row * width + col;
        if (visited[idx]) return;
        visited[idx] = true;
        queue.add(new long[]{ filled[row][col], row, col });
    }
}
