package com.github.xandergos.terraindiffusionmc.debug.cost;

public record TerrainCostTile(
        int blockStartX,
        int blockStartZ,
        int width,
        int height,
        int[] surfaceY,
        float[] totalCost,
        float[] slopeCost,
        float[] ridgeCost,
        float[] valleyBonus,
        float[] biomeCost,
        float[] soilCost,
        float[] forbiddenCost
) {
    public int index(int localX, int localZ) {
        return localZ * width + localX;
    }

    public int surfaceYAtLocal(int localX, int localZ) {
        return surfaceY[index(localX, localZ)];
    }

    public float totalAt(int localX, int localZ) {
        return totalCost[index(localX, localZ)];
    }

    public float slopeAt(int localX, int localZ) {
        return slopeCost[index(localX, localZ)];
    }

    public float ridgeAt(int localX, int localZ) {
        return ridgeCost[index(localX, localZ)];
    }

    public float valleyAt(int localX, int localZ) {
        return valleyBonus[index(localX, localZ)];
    }

    public float biomeAt(int localX, int localZ) {
        return biomeCost[index(localX, localZ)];
    }

    public float soilAt(int localX, int localZ) {
        return soilCost[index(localX, localZ)];
    }

    public float forbiddenAt(int localX, int localZ) {
        return forbiddenCost[index(localX, localZ)];
    }
}
