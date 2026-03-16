package com.github.xandergos.terraindiffusionmc.pipeline;

public class PipelineTest {
    public static void main(String[] args) throws Exception {
        long seed = -5408366058459925370L;
        int scale = 2;
        int TILE_SIZE = 256;

        // Block coords
        int blockX = -16160, blockZ = -59510;

        // Tile alignment (same as TerrainDiffusionDensityFunction, TILE_SHIFT=8)
        int blockStartX = (blockX >> 8) << 8;
        int blockStartZ = (blockZ >> 8) << 8;
        int blockEndX = blockStartX + TILE_SIZE;
        int blockEndZ = blockStartZ + TILE_SIZE;

        System.out.printf("blockStart: X=%d Z=%d  blockEnd: X=%d Z=%d%n",
                blockStartX, blockStartZ, blockEndX, blockEndZ);

        // Native pixel coords (floorDiv to match handleUpsampled exactly)
        int i1n = Math.floorDiv(blockStartZ, scale);
        int j1n = Math.floorDiv(blockStartX, scale);
        int i2n = -Math.floorDiv(-blockEndZ, scale);
        int j2n = -Math.floorDiv(-blockEndX, scale);
        int i1p = i1n - 2, j1p = j1n - 2;
        int i2p = i2n + 2, j2p = j2n + 2;
        int nH = i2p - i1p, nW = j2p - j1p;

        System.out.printf("Native range: i=[%d,%d) j=[%d,%d) nH=%d nW=%d%n",
                i1p, i2p, j1p, j2p, nH, nW);

        try (WorldPipeline pipeline = new WorldPipeline(seed)) {
            float[] elevNative = pipeline.get(i1p, j1p, i2p, j2p, false)[0];

            // Bilinear upsample nH*nW -> nH*scale x nW*scale
            float[][] native2D = new float[nH][nW];
            for (int r = 0; r < nH; r++)
                System.arraycopy(elevNative, r * nW, native2D[r], 0, nW);
            float[][] elevUp = LaplacianUtils.bilinearResize(native2D, nH * scale, nW * scale);

            // Crop offset (same as handleUpsampled)
            int padUp = 2 * scale;
            int offsetI = blockStartZ - i1n * scale;
            int offsetJ = blockStartX - j1n * scale;
            int cropI1 = padUp + offsetI;
            int cropJ1 = padUp + offsetJ;

            int localZ = blockZ - blockStartZ;
            int localX = blockX - blockStartX;
            float elev = elevUp[cropI1 + localZ][cropJ1 + localX];

            System.out.printf("Pipeline elev at block (X=%d Z=%d): %.2f m%n", blockX, blockZ, elev);
            System.out.printf("Native pixel approx: i=%.1f j=%.1f%n",
                    i1p + (cropI1 + localZ) / (float)scale,
                    j1p + (cropJ1 + localX) / (float)scale);

            // HeightConverter (gamma=1.0, c=30.0, resolution=30/scale)
            float RESOLUTION = 30f / scale;
            float GAMMA = 1.0f, C = 30.0f;
            int SEA_LEVEL = 63;
            double transformed = Math.pow(elev + C, GAMMA) - Math.pow(C, GAMMA);
            int baseY = (int)(transformed / RESOLUTION);
            int mcY = baseY + SEA_LEVEL;

            System.out.printf("Expected Minecraft Y at (X=%d Z=%d): %d  (resolution=%.1f)%n",
                    blockX, blockZ, mcY, RESOLUTION);
            System.out.printf("User reports: ~420 blocks%n");
        }
    }
}
