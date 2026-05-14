package com.github.xandergos.terraindiffusionmc.debug.flow;

public record TerrainFlowTile(
        int blockStartX,
        int blockStartZ,
        int width,
        int height,
        int[] surfaceY,
        byte[] initialDirection,
        byte[] direction,
        float[] dropMeters,
        float[] uphillMeters,
        float[] selectedCost,
        float[] score,
        boolean[] sink,
        float[] accumulationPreview,
        float[] accumulationFinal,
        float[] convergenceBonus,
        boolean[] changedByConvergence,
        float maxAccumulationPreview,
        float maxAccumulationFinal
) {
    public static final byte SINK_DIRECTION = -1;

    /** D8 order : N, NE, E, SE, S, SW, W, NW. */
    private static final int[] DIR_X = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DIR_Z = {-1, -1, 0, 1, 1, 1, 0, -1};

    public TerrainFlowTile {
        int expectedLength = width * height;
        if (surfaceY.length != expectedLength
                || initialDirection.length != expectedLength
                || direction.length != expectedLength
                || dropMeters.length != expectedLength
                || uphillMeters.length != expectedLength
                || selectedCost.length != expectedLength
                || score.length != expectedLength
                || sink.length != expectedLength
                || accumulationPreview.length != expectedLength
                || accumulationFinal.length != expectedLength
                || convergenceBonus.length != expectedLength
                || changedByConvergence.length != expectedLength) {
            throw new IllegalArgumentException("All flow tile arrays must have width * height elements");
        }
    }

    public int index(int localX, int localZ) {
        return localZ * width + localX;
    }

    public int surfaceYAtLocal(int localX, int localZ) {
        return surfaceY[index(localX, localZ)];
    }

    public byte initialDirectionAt(int localX, int localZ) {
        return initialDirection[index(localX, localZ)];
    }

    public byte directionAt(int localX, int localZ) {
        return direction[index(localX, localZ)];
    }

    public boolean isSinkAt(int localX, int localZ) {
        return sink[index(localX, localZ)];
    }

    public float dropAt(int localX, int localZ) {
        return dropMeters[index(localX, localZ)];
    }

    public float uphillAt(int localX, int localZ) {
        return uphillMeters[index(localX, localZ)];
    }

    public float selectedCostAt(int localX, int localZ) {
        return selectedCost[index(localX, localZ)];
    }

    public float scoreAt(int localX, int localZ) {
        return score[index(localX, localZ)];
    }

    public float accumulationPreviewAt(int localX, int localZ) {
        return accumulationPreview[index(localX, localZ)];
    }

    public float accumulationFinalAt(int localX, int localZ) {
        return accumulationFinal[index(localX, localZ)];
    }

    public float drainageAreaFractionAt(int localX, int localZ) {
        return maxAccumulationFinal <= 1.0F ? 0.0F : accumulationFinalAt(localX, localZ) / maxAccumulationFinal;
    }

    public float convergenceBonusAt(int localX, int localZ) {
        return convergenceBonus[index(localX, localZ)];
    }

    public boolean changedByConvergenceAt(int localX, int localZ) {
        return changedByConvergence[index(localX, localZ)];
    }

    public boolean isRiverPreviewAt(int localX, int localZ) {
        if (isSinkAt(localX, localZ)) {
            return false;
        }

        float accumulation = accumulationFinalAt(localX, localZ);
        if (accumulation < TerrainFlowConfig.RIVER_PREVIEW_MIN_ACCUMULATION) {
            return false;
        }

        float denominator = (float) Math.log1p(Math.max(1.0F, maxAccumulationFinal));
        if (denominator <= 0.0F) {
            return false;
        }
        float normalized = (float) (Math.log1p(Math.max(0.0F, accumulation)) / denominator);
        return normalized >= TerrainFlowConfig.RIVER_PREVIEW_LOG_THRESHOLD;
    }

    public int dirXAt(int localX, int localZ) {
        byte dir = directionAt(localX, localZ);
        return dir < 0 ? 0 : DIR_X[dir];
    }

    public int dirZAt(int localX, int localZ) {
        byte dir = directionAt(localX, localZ);
        return dir < 0 ? 0 : DIR_Z[dir];
    }

    public static int dirX(byte direction) {
        return direction < 0 ? 0 : DIR_X[direction];
    }

    public static int dirZ(byte direction) {
        return direction < 0 ? 0 : DIR_Z[direction];
    }
}
