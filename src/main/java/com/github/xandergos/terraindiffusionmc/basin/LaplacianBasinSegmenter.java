package com.github.xandergos.terraindiffusionmc.basin;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Segments a heightmap into irrigation basins using the sign of the discrete Laplacian.
 *
 * <p>Cells where {@code Laplace(h) > 0} (locally below their 4-neighborhood average) are treated
 * as concave and grouped into basins by 4-connected flood fill. Convex cells separate basins
 * and receive label {@link #LABEL_RIDGE}. Basins smaller than {@link #MIN_BASIN_SIZE} cells are
 * pruned and their cells are reassigned to {@link #LABEL_RIDGE}.
 *
 * <p>This is a purely geometric segmentation: it ignores hydrological flow direction.
 * Flow-based refinements (D8 inlet/outlet detection) are intended to layer on top of these labels.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class LaplacianBasinSegmenter {

    /** Label assigned to convex cells (ridges) that are not part of any basin. */
    public static final int LABEL_RIDGE = -1;

    /** Minimum cell count for a basin to survive pruning. */
    private static final int MIN_BASIN_SIZE = 128;

    private LaplacianBasinSegmenter() {
    }

    /**
     * Result of segmenting a heightmap region.
     */
    public static final class BasinSegmentation {
        /** Per-cell basin id and row-major ({@code height * width}). {@link #LABEL_RIDGE} for ridge cells. */
        public final int[] labels;
        /** Number of distinct basins after pruning (ids run from 0 to {@code basinCount - 1}). */
        public final int basinCount;
        public final int width;
        public final int height;

        public BasinSegmentation(int[] labels, int basinCount, int width, int height) {
            this.labels = labels;
            this.basinCount = basinCount;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Segments a heightmap into basins.
     *
     * @param heightmap row-major elevation samples in block units
     * @param width     number of columns (X axis)
     * @param height    number of rows (Z axis)
     * @return labeled segmentation with compacted basin ids and ridge cells
     */
    public static BasinSegmentation segment(short[][] heightmap, int width, int height) {
        boolean[] isConcave = computeConcavityMask(heightmap, width, height);
        int[] labels = floodFillConcaveRegions(isConcave, width, height);
        int basinCount = pruneSmallBasins(labels, width, height);
        return new BasinSegmentation(labels, basinCount, width, height);
    }

    /**
     * Computes the discrete Laplacian sign mask : {@code true} where {@code Laplace(h) > 0} (concave).
     * Boundary cells use replicated-edge padding so they are classified consistently.
     */
    private static boolean[] computeConcavityMask(short[][] heightmap, int width, int height) {
        boolean[] isConcave = new boolean[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int center = heightmap[row][col];
                int north = heightmap[Math.max(0, row - 1)][col];
                int south = heightmap[Math.min(height - 1, row + 1)][col];
                int west = heightmap[row][Math.max(0, col - 1)];
                int east = heightmap[row][Math.min(width - 1, col + 1)];
                int laplacian = (north + south + west + east) - 4 * center;
                isConcave[row * width + col] = laplacian > 0;
            }
        }
        return isConcave;
    }

    /**
     * 4-connected flood fill over concave cells. Convex cells are left at {@link #LABEL_RIDGE}.
     */
    private static int[] floodFillConcaveRegions(boolean[] isConcave, int width, int height) {
        int[] labels = new int[width * height];
        Arrays.fill(labels, LABEL_RIDGE);

        int nextLabel = 0;
        Deque<int[]> stack = new ArrayDeque<>();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int seedIndex = row * width + col;
                if (!isConcave[seedIndex] || labels[seedIndex] != LABEL_RIDGE) {
                    continue;
                }
                stack.push(new int[]{row, col});
                labels[seedIndex] = nextLabel;
                while (!stack.isEmpty()) {
                    int[] cell = stack.pop();
                    int cellRow = cell[0];
                    int cellCol = cell[1];
                    tryEnqueue(stack, labels, isConcave, cellRow - 1, cellCol, width, height, nextLabel);
                    tryEnqueue(stack, labels, isConcave, cellRow + 1, cellCol, width, height, nextLabel);
                    tryEnqueue(stack, labels, isConcave, cellRow, cellCol - 1, width, height, nextLabel);
                    tryEnqueue(stack, labels, isConcave, cellRow, cellCol + 1, width, height, nextLabel);
                }
                nextLabel++;
            }
        }
        return labels;
    }

    private static void tryEnqueue(Deque<int[]> stack, int[] labels, boolean[] isConcave,
                                   int row, int col, int width, int height, int label) {
        if (row < 0 || row >= height || col < 0 || col >= width) return;
        int index = row * width + col;
        if (!isConcave[index] || labels[index] != LABEL_RIDGE) return;
        labels[index] = label;
        stack.push(new int[]{row, col});
    }

    /**
     * Prunes basins smaller than {@link #MIN_BASIN_SIZE} cells (their cells become {@link #LABEL_RIDGE})
     * then re-numbers surviving basins contiguously from 0. Returns the new basin count.
     */
    private static int pruneSmallBasins(int[] labels, int width, int height) {
        int rawBasinCount = 0;
        for (int label : labels) if (label > rawBasinCount) rawBasinCount = label;
        rawBasinCount++;

        int[] cellCounts = new int[rawBasinCount];
        for (int label : labels) if (label != LABEL_RIDGE) cellCounts[label]++;

        int[] remap = new int[rawBasinCount];
        int keptCount = 0;
        for (int label = 0; label < rawBasinCount; label++) {
            remap[label] = cellCounts[label] >= MIN_BASIN_SIZE ? keptCount++ : LABEL_RIDGE;
        }

        for (int i = 0; i < labels.length; i++) {
            if (labels[i] != LABEL_RIDGE) labels[i] = remap[labels[i]];
        }
        return keptCount;
    }
}
