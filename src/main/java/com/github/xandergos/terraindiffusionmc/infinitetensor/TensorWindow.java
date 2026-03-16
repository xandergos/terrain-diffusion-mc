package com.github.xandergos.terraindiffusionmc.infinitetensor;

/**
 * Defines the sliding window layout for an InfiniteTensor.
 *
 * For window index w[], the covered pixel range in dimension d is:
 *   [w[d] * stride[d] + offset[d],  w[d] * stride[d] + offset[d] + size[d])
 *
 * Windows may overlap (stride < size) or have gaps (stride > size).
 * Overlapping windows are summed during slice accumulation.
 */
public class TensorWindow {
    public final int[] size;
    public final int[] stride;
    public final int[] offset;

    public TensorWindow(int[] size, int[] stride, int[] offset) {
        this.size = size.clone();
        this.stride = stride.clone();
        this.offset = offset.clone();
    }

    /** Non-overlapping windows starting at zero. */
    public TensorWindow(int[] size) {
        this.size = size.clone();
        this.stride = size.clone();
        this.offset = new int[size.length];
    }

    /** Overlapping windows with given stride, starting at zero. */
    public TensorWindow(int[] size, int[] stride) {
        this.size = size.clone();
        this.stride = stride.clone();
        this.offset = new int[size.length];
    }

    public int ndim() {
        return size.length;
    }

    /**
     * Returns the pixel-space bounds [start, stop) for the given window index.
     * result[d] = {start, stop}.
     */
    public int[][] getBounds(int[] windowIndex) {
        int n = size.length;
        int[][] bounds = new int[n][2];
        for (int i = 0; i < n; i++) {
            bounds[i][0] = windowIndex[i] * stride[i] + offset[i];
            bounds[i][1] = windowIndex[i] * stride[i] + offset[i] + size[i];
        }
        return bounds;
    }

    /**
     * Returns the lowest window index (per dimension) whose bounds overlap the pixel range.
     * pixelRange[d] = {start, stop}.
     *
     * Solves: pixelRange[d].start < w * stride + offset + size
     * i.e. w > (p - offset - size) / stride
     */
    public int[] getLowestIntersection(int[][] pixelRange) {
        int n = size.length;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            int p = pixelRange[i][0];
            int numerator = p - offset[i] - size[i] + 1;
            if (numerator >= 0) {
                // ceiling division
                result[i] = (numerator + stride[i] - 1) / stride[i];
            } else {
                // ceiling for negative: -(floor(-num / stride))
                result[i] = -((-numerator) / stride[i]);
            }
        }
        return result;
    }

    /**
     * Returns the highest window index (per dimension) whose bounds overlap the pixel range.
     * pixelRange[d] = {start, stop}.
     *
     * Solves: w * stride + offset <= pixelRange[d].stop - 1
     * i.e. w <= (p - offset) / stride  (floor division)
     */
    public int[] getHighestIntersection(int[][] pixelRange) {
        int n = size.length;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            int p = pixelRange[i][1] - 1;
            result[i] = Math.floorDiv(p - offset[i], stride[i]);
        }
        return result;
    }
}
