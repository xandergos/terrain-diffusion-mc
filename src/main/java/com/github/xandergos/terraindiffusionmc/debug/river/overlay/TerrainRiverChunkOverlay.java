package com.github.xandergos.terraindiffusionmc.debug.river.overlay;

public record TerrainRiverChunkOverlay(
        int blockStartX,
        int blockStartZ,
        int width,
        int height,
        int[] surfaceY,
        float[] distanceNormalized,
        float[] bedStrength,
        float[] waterStrength,
        float[] bankStrength,
        float[] materialStrength,
        float[] vegetationStrength,
        float[] terrainCorrection,
        float maxTerrainCorrection
) {
    public TerrainRiverChunkOverlay {
        int len = width * height;
        if (surfaceY.length != len
                || distanceNormalized.length != len
                || bedStrength.length != len
                || waterStrength.length != len
                || bankStrength.length != len
                || materialStrength.length != len
                || vegetationStrength.length != len
                || terrainCorrection.length != len) {
            throw new IllegalArgumentException("All river chunk overlay arrays must have width * height elements");
        }
    }

    public int index(int localX, int localZ) {
        return localZ * width + localX;
    }

    public int surfaceYAtLocal(int localX, int localZ) {
        return surfaceY[index(localX, localZ)];
    }

    public float distanceNormalizedAt(int localX, int localZ) {
        return distanceNormalized[index(localX, localZ)];
    }

    public float bedAt(int localX, int localZ) {
        return bedStrength[index(localX, localZ)];
    }

    public float waterAt(int localX, int localZ) {
        return waterStrength[index(localX, localZ)];
    }

    public float bankAt(int localX, int localZ) {
        return bankStrength[index(localX, localZ)];
    }

    public float materialAt(int localX, int localZ) {
        return materialStrength[index(localX, localZ)];
    }

    public float vegetationAt(int localX, int localZ) {
        return vegetationStrength[index(localX, localZ)];
    }

    public float terrainCorrectionAt(int localX, int localZ) {
        return terrainCorrection[index(localX, localZ)];
    }
}
