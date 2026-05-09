package com.github.xandergos.terraindiffusionmc.debug.river.global;

import java.util.List;

/**
 * Directed edge in the global river graph.
 */
public record RiverSegment(
        long id,
        long startNodeId,
        long endNodeId,
        List<RiverPoint> polyline,
        float meanAccumulation,
        float maxAccumulation,
        float discharge,
        float meanWidthBlocks,
        float maxWidthBlocks,
        float depthBlocks,
        byte downstreamDirection,
        Bounds bounds
) {
    public RiverSegment {
        if (polyline.size() < 2) {
            throw new IllegalArgumentException("A river segment must contain at least two polyline points");
        }
        polyline = List.copyOf(polyline);
        meanAccumulation = Math.max(0.0F, meanAccumulation);
        maxAccumulation = Math.max(0.0F, maxAccumulation);
        discharge = Math.max(0.0F, discharge);
        meanWidthBlocks = Math.max(0.0F, meanWidthBlocks);
        maxWidthBlocks = Math.max(0.0F, maxWidthBlocks);
        depthBlocks = Math.max(0.0F, depthBlocks);
        bounds = bounds == null ? Bounds.from(polyline) : bounds;
    }

    public boolean intersectsAabb(double minX, double minZ, double maxX, double maxZ) {
        return bounds.intersects(minX, minZ, maxX, maxZ);
    }

    public record Bounds(
            double minX,
            double minZ,
            double maxX,
            double maxZ,
            int minSurfaceY,
            int maxSurfaceY
    ) {
        public Bounds {
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("Invalid river segment bounds");
            }
        }

        public boolean intersects(double otherMinX, double otherMinZ, double otherMaxX, double otherMaxZ) {
            return maxX >= otherMinX && minX <= otherMaxX && maxZ >= otherMinZ && minZ <= otherMaxZ;
        }

        public static Bounds from(List<RiverPoint> points) {
            double minX = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;

            for (RiverPoint point : points) {
                minX = Math.min(minX, point.worldX());
                minZ = Math.min(minZ, point.worldZ());
                maxX = Math.max(maxX, point.worldX());
                maxZ = Math.max(maxZ, point.worldZ());
                minY = Math.min(minY, point.surfaceY());
                maxY = Math.max(maxY, point.surfaceY());
            }

            return new Bounds(minX, minZ, maxX, maxZ, minY, maxY);
        }
    }
}
