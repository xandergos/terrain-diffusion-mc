package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Portable RNG matching terrain_diffusion/inference/portable_rng.py and
 * world_pipeline._tile_seed. PCG64 (64-bit LCG + XSH-RR 64/32) and
 * standard normal via Marsaglia polar. Same algorithm as Python for identical streams.
 */
public final class PortableRng {

    private static final long PCG64_MULT = 6364136223846793005L;
    private static final long PCG64_INC = 1442695040888963407L;
    private static final long MASK64 = 0xFFFFFFFFFFFFFFFFL;
    private static final double INV_2P32 = 1.0 / 4294967296.0;

    /**
     * Portable 64-bit seed from (base_seed, ty, tx). Matches world_pipeline._tile_seed.
     * Uses full 64-bit base seed (Python: seed & 0xFFFFFFFFFFFFFFFF).
     */
    public static long tileSeed(long baseSeed, int ty, int tx) {
        long h = (baseSeed & MASK64) * 0x9E3779B9L;
        h = (h + (ty & 0xFFFFFFFFL)) & MASK64;
        h = (h * 0x9E3779B9L + (tx & 0xFFFFFFFFL)) & MASK64;
        return h;
    }

    /**
     * One PCG64 step: (state * MULT + INC) & MASK64, output 32-bit XSH-RR.
     * Returns { newState (64-bit), output32 (unsigned 32-bit as long) }.
     */
    public static long[] pcg64Next(long state) {
        state = (state * PCG64_MULT + PCG64_INC) & MASK64;
        long x = (((state >>> 18) ^ state) >>> 27) & 0xFFFFFFFFL;
        int rot = (int) (state >>> 59);
        long out32 = ((x >>> rot) | (x << ((32 - rot) & 31))) & 0xFFFFFFFFL;
        return new long[]{state, out32};
    }

    /**
     * Fill out[offset..offset+length) with standard normals using Marsaglia polar.
     * Matches portable_rng._fill_standard_normal_impl.
     */
    public static void fillStandardNormal(long seed, float[] out, int offset, int length) {
        long state = seed & MASK64;
        int i = 0;
        while (i < length) {
            long[] r1 = pcg64Next(state);
            state = r1[0];
            long[] r2 = pcg64Next(state);
            state = r2[0];
            double v1 = 2.0 * (r1[1] + 1.0) * INV_2P32 - 1.0;
            double v2 = 2.0 * (r2[1] + 1.0) * INV_2P32 - 1.0;
            double s = v1 * v1 + v2 * v2;
            if (s > 0.0 && s < 1.0) {
                double f = Math.sqrt(-2.0 * Math.log(s) / s);
                out[offset + i] = (float) (v1 * f);
                i++;
                if (i < length) {
                    out[offset + i] = (float) (v2 * f);
                    i++;
                }
            }
        }
    }
}
