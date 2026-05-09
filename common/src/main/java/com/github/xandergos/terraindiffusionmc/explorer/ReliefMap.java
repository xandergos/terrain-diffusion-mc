package com.github.xandergos.terraindiffusionmc.explorer;

/**
 * Java port of terrain_diffusion/inference/relief_map.py:get_relief_map().
 *
 * <p>Produces an RGB shaded-relief image from a (H×W) elevation grid (metres).
 * Same algorithm, constants, and control flow as the Python original.
 */
public final class ReliefMap {

    private ReliefMap() {}

    /**
     * Compute a shaded-relief RGB image.
     *
     * @param elev     elevation in metres, row-major, length H*W
     * @param H        height (rows)
     * @param W        width (columns)
     * @param resolution metres per pixel (use 90.0 to match Python explorer default)
     * @return float[3][H*W] — RGB channels, values in [0, 1]
     */
    public static float[][] getReliefMap(float[] elev, int H, int W, double resolution) {
        // Replace NaNs with median (matching Python nan_to_num logic)
        float[] elevF = replaceNaN(elev, H, W);

        // Multi-scale hillshade: sigma_large=6, sigma_small=1.2 (Python defaults)
        float[] elevLarge = gaussianBlur(elevF, H, W, 6.0f);
        float[] elevSmall = gaussianBlur(elevF, H, W, 1.2f);

        float[] hsLarge = hillshade(elevLarge, H, W, 315.0, 45.0, resolution);
        float[] hsSmall = hillshade(elevSmall, H, W, 315.0, 45.0, resolution);

        // hillshade = clip(0.75 * hs_large + 0.25 * hs_small, 0, 1)
        // then power(hillshade, 0.85)
        float[] hs = new float[H * W];
        for (int i = 0; i < hs.length; i++) {
            hs[i] = (float) Math.pow(Math.min(1f, Math.max(0f, 0.75f * hsLarge[i] + 0.25f * hsSmall[i])), 0.85);
        }

        // Elevation colormap for land (terrain cmap, land range mapped to 0.25–1.0)
        float landMin = 0f, landMax = 0f;
        for (float v : elevF) {
            float land = Math.max(0f, v);
            if (land > landMax) landMax = land;
        }
        float span = landMax - landMin + 1e-8f;

        float[] r = new float[H * W];
        float[] g = new float[H * W];
        float[] b = new float[H * W];

        for (int i = 0; i < H * W; i++) {
            float land = Math.max(0f, elevF[i]);
            float norm = (land - landMin) / span;
            float normCmap = 0.25f + (float) Math.pow(Math.min(1f, Math.max(0f, norm)), 0.7) * 0.75f;
            float[] rgb = Colormaps.terrain(normCmap);
            r[i] = rgb[0];
            g[i] = rgb[1];
            b[i] = rgb[2];
        }

        // GDAL-like intensity blend: intensity = 0.35 + 0.65 * hillshade
        for (int i = 0; i < H * W; i++) {
            float intensity = 0.35f + 0.65f * hs[i];
            r[i] = Math.min(1f, Math.max(0f, r[i] * intensity));
            g[i] = Math.min(1f, Math.max(0f, g[i] * intensity));
            b[i] = Math.min(1f, Math.max(0f, b[i] * intensity));
        }

        // Ocean coloring: elev < 0 → depth-based blue gradient
        // coast_color = [0.68, 0.88, 1.00], deep_color = [0.00, 0.10, 0.45], max_depth = 10000
        for (int i = 0; i < H * W; i++) {
            float e = elevF[i];
            if (e < 0f) {
                float depth = -e;
                float t = (float) Math.pow(Math.min(1f, depth / 10000f), 0.7);
                r[i] = (1f - t) * 0.68f + t * 0.00f;
                g[i] = (1f - t) * 0.88f + t * 0.10f;
                b[i] = (1f - t) * 1.00f + t * 0.45f;
            }
        }

        return new float[][]{r, g, b};
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Hillshade using GDAL-style formula (port of compute_hillshade in relief_map.py).
     * azimuth=315°, altitude=45°, gradient divisor = 15 * resolution/90.
     */
    private static float[] hillshade(float[] src, int H, int W, double azimuthDeg, double altitudeDeg, double resolution) {
        double[] dy = new double[H * W];
        double[] dx = new double[H * W];
        double divisor = 15.0 * resolution / 90.0;

        // np.gradient: central differences for interior, forward/backward at edges
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                // dy = d(src)/d(row)
                if (r == 0) {
                    dy[idx] = src[(r + 1) * W + c] - src[r * W + c];
                } else if (r == H - 1) {
                    dy[idx] = src[r * W + c] - src[(r - 1) * W + c];
                } else {
                    dy[idx] = (src[(r + 1) * W + c] - src[(r - 1) * W + c]) / 2.0;
                }
                // dx = d(src)/d(col)
                if (c == 0) {
                    dx[idx] = src[r * W + (c + 1)] - src[r * W + c];
                } else if (c == W - 1) {
                    dx[idx] = src[r * W + c] - src[r * W + (c - 1)];
                } else {
                    dx[idx] = (src[r * W + (c + 1)] - src[r * W + (c - 1)]) / 2.0;
                }
                dy[idx] /= divisor;
                dx[idx] /= divisor;
            }
        }

        double azRad  = Math.toRadians(azimuthDeg);
        double altRad = Math.toRadians(altitudeDeg);
        float[] hs = new float[H * W];
        for (int i = 0; i < H * W; i++) {
            double slopeRad  = Math.PI / 2.0 - Math.atan(Math.hypot(dx[i], dy[i]));
            double aspectRad = Math.atan2(dy[i], -dx[i]);
            double val = Math.sin(altRad) * Math.sin(slopeRad)
                    + Math.cos(altRad) * Math.cos(slopeRad) * Math.cos(azRad - aspectRad);
            hs[i] = (float) Math.min(1.0, Math.max(0.0, val));
        }
        return hs;
    }

    /** Separable Gaussian blur (port of scipy.ndimage.gaussian_filter). */
    static float[] gaussianBlur(float[] src, int H, int W, float sigma) {
        int radius = Math.max(1, (int) Math.ceil(3 * sigma));
        float[] kernel = makeGaussianKernel(sigma, radius);

        float[] tmp = new float[H * W];
        // Horizontal pass
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                float sum = 0f, weight = 0f;
                for (int k = -radius; k <= radius; k++) {
                    int cc = Math.min(W - 1, Math.max(0, c + k));
                    float w = kernel[k + radius];
                    sum += src[r * W + cc] * w;
                    weight += w;
                }
                tmp[r * W + c] = sum / weight;
            }
        }
        float[] out = new float[H * W];
        // Vertical pass
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                float sum = 0f, weight = 0f;
                for (int k = -radius; k <= radius; k++) {
                    int rr = Math.min(H - 1, Math.max(0, r + k));
                    float w = kernel[k + radius];
                    sum += tmp[rr * W + c] * w;
                    weight += w;
                }
                out[r * W + c] = sum / weight;
            }
        }
        return out;
    }

    private static float[] makeGaussianKernel(float sigma, int radius) {
        int size = 2 * radius + 1;
        float[] k = new float[size];
        float s2 = 2 * sigma * sigma;
        for (int i = 0; i < size; i++) {
            int d = i - radius;
            k[i] = (float) Math.exp(-(d * d) / s2);
        }
        return k;
    }

    private static float[] replaceNaN(float[] src, int H, int W) {
        float[] out = src.clone();
        // Compute median of finite values
        float sum = 0f;
        int count = 0;
        for (float v : out) {
            if (!Float.isNaN(v) && Float.isFinite(v)) {
                sum += v;
                count++;
            }
        }
        float median = count > 0 ? sum / count : 0f;
        for (int i = 0; i < out.length; i++) {
            if (Float.isNaN(out[i])) out[i] = median;
        }
        return out;
    }
}
