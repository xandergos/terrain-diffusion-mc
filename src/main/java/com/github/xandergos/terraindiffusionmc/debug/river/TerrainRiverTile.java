package com.github.xandergos.terraindiffusionmc.debug.river;

public record TerrainRiverTile(
        int blockStartX,
        int blockStartZ,
        int width,
        int height,
        int[] surfaceY,
        boolean[] river,
        boolean[] source,
        boolean[] confluence,
        boolean[] outlet,
        byte[] direction,
        byte[] upstreamRiverCount,
        float[] accumulation,
        float[] logStrength,
        float[] widthBlocks,
        float maxAccumulation
) {
    public TerrainRiverTile {
        int expectedLength = width * height;
        if (surfaceY.length != expectedLength
                || river.length != expectedLength
                || source.length != expectedLength
                || confluence.length != expectedLength
                || outlet.length != expectedLength
                || direction.length != expectedLength
                || upstreamRiverCount.length != expectedLength
                || accumulation.length != expectedLength
                || logStrength.length != expectedLength
                || widthBlocks.length != expectedLength) {
            throw new IllegalArgumentException("All river tile arrays must have width * height elements");
        }
    }

    public int index(int localX, int localZ) {
        return localZ * width + localX;
    }

    public int surfaceYAtLocal(int localX, int localZ) {
        return surfaceY[index(localX, localZ)];
    }

    public boolean isRiverAt(int localX, int localZ) {
        return river[index(localX, localZ)];
    }

    public boolean isSourceAt(int localX, int localZ) {
        return source[index(localX, localZ)];
    }

    public boolean isConfluenceAt(int localX, int localZ) {
        return confluence[index(localX, localZ)];
    }

    public boolean isOutletAt(int localX, int localZ) {
        return outlet[index(localX, localZ)];
    }

    public byte directionAt(int localX, int localZ) {
        return direction[index(localX, localZ)];
    }

    public int upstreamRiverCountAt(int localX, int localZ) {
        return upstreamRiverCount[index(localX, localZ)] & 255;
    }

    public float accumulationAt(int localX, int localZ) {
        return accumulation[index(localX, localZ)];
    }

    public float logStrengthAt(int localX, int localZ) {
        return logStrength[index(localX, localZ)];
    }

    public float widthBlocksAt(int localX, int localZ) {
        return widthBlocks[index(localX, localZ)];
    }
}
