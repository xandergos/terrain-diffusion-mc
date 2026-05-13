package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Deterministic tile-seeded Gaussian noise matching Python world_pipeline.gaussian_noise_patch.
 * Uses portable_rng (PCG64 + Marsaglia polar) and _tile_seed; same algorithm as Python.
 */
public final class GaussianNoisePatch {

    private static final int DEFAULT_TILE_H = 256;
    private static final int DEFAULT_TILE_W = 256;

    /**
     * Returns a (channels, h, w) patch of standard-normal noise.
     *
     * @param baseSeed  world seed (64-bit, matches Python WorldPipeline.seed)
     * @param y0        top pixel row (can be negative)
     * @param x0        left pixel column (can be negative)
     * @param h         output height in pixels
     * @param w         output width in pixels
     * @param channels  number of channels
     * @param tileH     tile height for seeding
     * @param tileW     tile width for seeding
     * @return float array [channels][h][w] with Gaussian values
     */
    public static float[][][] generate(long baseSeed, int y0, int x0, int h, int w,
                                       int channels, int tileH, int tileW) {
        float[][][] out = new float[channels][h][w];

        int ty0 = Math.floorDiv(y0, tileH);
        int ty1 = Math.floorDiv(y0 + h - 1, tileH);
        int tx0 = Math.floorDiv(x0, tileW);
        int tx1 = Math.floorDiv(x0 + w - 1, tileW);

        for (int ty = ty0; ty <= ty1; ty++) {
            int tileY0 = ty * tileH;
            for (int tx = tx0; tx <= tx1; tx++) {
                int tileX0 = tx * tileW;

                int oy0 = Math.max(y0, tileY0);
                int oy1 = Math.min(y0 + h, tileY0 + tileH);
                int ox0 = Math.max(x0, tileX0);
                int ox1 = Math.min(x0 + w, tileX0 + tileW);

                long seed = PortableRng.tileSeed(baseSeed, ty, tx);
                int tileLen = channels * tileH * tileW;
                float[] tileFlat = new float[tileLen];
                PortableRng.fillStandardNormal(seed, tileFlat, 0, tileLen);

                for (int c = 0; c < channels; c++) {
                    for (int py = oy0; py < oy1; py++) {
                        int outY = py - y0;
                        int tilePy = py - tileY0;
                        for (int px = ox0; px < ox1; px++) {
                            int outX = px - x0;
                            int tilePx = px - tileX0;
                            out[c][outY][outX] = tileFlat[c * (tileH * tileW) + tilePy * tileW + tilePx];
                        }
                    }
                }
            }
        }
        return out;
    }

    public static float[][][] generate(long baseSeed, int y0, int x0, int h, int w, int channels) {
        return generate(baseSeed, y0, x0, h, w, channels, DEFAULT_TILE_H, DEFAULT_TILE_W);
    }

    /** Generate with tile size matching the requested region (per-tile in pipeline). */
    public static float[][][] generateTileSeeded(long baseSeed, int y0, int x0, int h, int w, int channels) {
        return generate(baseSeed, y0, x0, h, w, channels, h, w);
    }
}
