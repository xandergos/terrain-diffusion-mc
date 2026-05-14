package com.github.xandergos.terraindiffusionmc.debug.river;

import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowTile;

public final class TerrainRiverBuilder {
    private static final int[] DIR_X = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DIR_Z = {-1, -1, 0, 1, 1, 1, 0, -1};

    private TerrainRiverBuilder() {
    }

    public static TerrainRiverTile build(TerrainFlowTile flow) {
        int width = flow.width();
        int height = flow.height();
        int len = width * height;

        int[] surfaceY = new int[len];
        boolean[] river = new boolean[len];
        boolean[] source = new boolean[len];
        boolean[] confluence = new boolean[len];
        boolean[] outlet = new boolean[len];
        byte[] direction = new byte[len];
        byte[] upstreamRiverCount = new byte[len];
        float[] accumulation = new float[len];
        float[] logStrength = new float[len];
        float[] widthBlocks = new float[len];

        float maxAccumulation = Math.max(1.0F, flow.maxAccumulationFinal());
        double logDenominator = Math.log1p(maxAccumulation);

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, z, width);
                float acc = flow.accumulationFinalAt(x, z);
                float log = logDenominator <= 0.0D
                        ? 0.0F
                        : clamp01((float) (Math.log1p(Math.max(0.0F, acc)) / logDenominator));

                surfaceY[idx] = flow.surfaceYAtLocal(x, z);
                direction[idx] = flow.directionAt(x, z);
                accumulation[idx] = acc;
                logStrength[idx] = log;

                boolean isRiver = !flow.isSinkAt(x, z)
                        && acc >= TerrainRiverConfig.MIN_ACCUMULATION
                        && log >= TerrainRiverConfig.LOG_THRESHOLD;

                river[idx] = isRiver;
                widthBlocks[idx] = isRiver ? widthFromStrength(log) : 0.0F;
            }
        }

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, z, width);
                if (!river[idx]) {
                    continue;
                }

                byte dir = direction[idx];
                if (dir < 0) {
                    outlet[idx] = true;
                    continue;
                }

                int nx = x + DIR_X[dir];
                int nz = z + DIR_Z[dir];
                if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                    outlet[idx] = true;
                    continue;
                }

                int nextIdx = index(nx, nz, width);
                if (!river[nextIdx]) {
                    outlet[idx] = true;
                    continue;
                }

                int previous = upstreamRiverCount[nextIdx] & 255;
                upstreamRiverCount[nextIdx] = (byte) Math.min(255, previous + 1);
            }
        }

        for (int idx = 0; idx < len; idx++) {
            if (!river[idx]) {
                continue;
            }

            int upstream = upstreamRiverCount[idx] & 255;
            source[idx] = upstream == 0;
            confluence[idx] = upstream >= 2;
        }

        return new TerrainRiverTile(
                flow.blockStartX(),
                flow.blockStartZ(),
                width,
                height,
                surfaceY,
                river,
                source,
                confluence,
                outlet,
                direction,
                upstreamRiverCount,
                accumulation,
                logStrength,
                widthBlocks,
                maxAccumulation
        );
    }

    private static float widthFromStrength(float logStrength) {
        float t = (float) Math.pow(clamp01(logStrength), 0.85D);
        return TerrainRiverConfig.MIN_WIDTH_BLOCKS
                + (TerrainRiverConfig.MAX_WIDTH_BLOCKS - TerrainRiverConfig.MIN_WIDTH_BLOCKS) * t;
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static int index(int x, int z, int width) {
        return z * width + x;
    }
}
