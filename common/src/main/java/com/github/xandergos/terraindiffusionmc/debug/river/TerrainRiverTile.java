package com.github.xandergos.terraindiffusionmc.debug.river;

public record TerrainRiverTile(
        int blockStartX,
        int blockStartZ,
        int width,
        int height,
        int[] surfaceY,
        boolean[] river,
        boolean[] lake,
        boolean[] ocean,
        boolean[] source,
        boolean[] confluence,
        boolean[] outlet,
        byte[] direction,
        byte[] upstreamRiverCount,
        byte[] traceRejectReason,
        float[] accumulation,
        float[] logStrength,
        float[] riverPotential,
        float[] widthBlocks,
        float[] depthBlocks,
        float[] waterLevelY,
        float[] meanderStrength,
        float maxAccumulation
) {
    public static final byte TRACE_REJECT_NONE = 0;
    public static final byte TRACE_REJECT_WEAK_SIGNAL = 1;
    public static final byte TRACE_REJECT_BROKEN_FLOW = 2;
    public static final byte TRACE_REJECT_LOOP = 3;
    public static final byte TRACE_REJECT_TOO_SHORT = 4;
    public static final byte TRACE_REJECT_DRY_BASIN = 5;
    public static final byte TRACE_REJECT_PRUNED_BRANCH = 6;
    public static final byte TRACE_REJECT_UNRESOLVED_GAP = 7;

    public TerrainRiverTile {
        int expectedLength = width * height;
        if (surfaceY.length != expectedLength
                || river.length != expectedLength
                || lake.length != expectedLength
                || ocean.length != expectedLength
                || source.length != expectedLength
                || confluence.length != expectedLength
                || outlet.length != expectedLength
                || direction.length != expectedLength
                || upstreamRiverCount.length != expectedLength
                || traceRejectReason.length != expectedLength
                || accumulation.length != expectedLength
                || logStrength.length != expectedLength
                || riverPotential.length != expectedLength
                || widthBlocks.length != expectedLength
                || depthBlocks.length != expectedLength
                || waterLevelY.length != expectedLength
                || meanderStrength.length != expectedLength) {
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

    public boolean isLakeAt(int localX, int localZ) {
        return lake[index(localX, localZ)];
    }

    public boolean isOceanAt(int localX, int localZ) {
        return ocean[index(localX, localZ)];
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

    public byte traceRejectReasonAt(int localX, int localZ) {
        return traceRejectReason[index(localX, localZ)];
    }

    public float accumulationAt(int localX, int localZ) {
        return accumulation[index(localX, localZ)];
    }

    public float dischargeAt(int localX, int localZ) {
        return accumulationAt(localX, localZ);
    }

    public float logStrengthAt(int localX, int localZ) {
        return logStrength[index(localX, localZ)];
    }

    public float riverPotentialAt(int localX, int localZ) {
        return riverPotential[index(localX, localZ)];
    }

    public float widthBlocksAt(int localX, int localZ) {
        return widthBlocks[index(localX, localZ)];
    }

    public float depthBlocksAt(int localX, int localZ) {
        return depthBlocks[index(localX, localZ)];
    }

    public float waterLevelYAt(int localX, int localZ) {
        return waterLevelY[index(localX, localZ)];
    }

    public float meanderStrengthAt(int localX, int localZ) {
        return meanderStrength[index(localX, localZ)];
    }
}
