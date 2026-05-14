package com.github.xandergos.terraindiffusionmc.debug.river.vector;

import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverTile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TerrainRiverVectorBuilder {
    private static final int[] DIR_X = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DIR_Z = {-1, -1, 0, 1, 1, 1, 0, -1};

    private TerrainRiverVectorBuilder() {
    }

    public static TerrainRiverNetwork build(TerrainRiverTile river) {
        VectorBuildContext context = new VectorBuildContext(river);

        for (int z = 0; z < river.height(); z++) {
            for (int x = 0; x < river.width(); x++) {
                if (isSegmentStart(river, x, z)) {
                    context.traceFrom(x, z);
                }
            }
        }

        // Fallback : catch isolated river chains that have no clean source inside the cropped tile.
        for (int z = 0; z < river.height(); z++) {
            for (int x = 0; x < river.width(); x++) {
                int idx = river.index(x, z);
                if (river.isRiverAt(x, z) && !context.edgeVisited[idx]) {
                    context.traceFrom(x, z);
                }
            }
        }

        return new TerrainRiverNetwork(
                river.blockStartX(),
                river.blockStartZ(),
                river.width(),
                river.height(),
                List.copyOf(context.nodes),
                List.copyOf(context.segments),
                river.maxAccumulation()
        );
    }

    private static boolean isSegmentStart(TerrainRiverTile river, int x, int z) {
        if (!river.isRiverAt(x, z)) {
            return false;
        }

        return river.isSourceAt(x, z)
                || river.isConfluenceAt(x, z)
                || isBoundaryCell(river, x, z);
    }

    private static boolean isNode(TerrainRiverTile river, int x, int z) {
        return river.isRiverAt(x, z)
                && (river.isSourceAt(x, z)
                || river.isConfluenceAt(x, z)
                || river.isOutletAt(x, z)
                || isBoundaryCell(river, x, z));
    }

    private static boolean isBoundaryCell(TerrainRiverTile river, int x, int z) {
        return x == 0 || z == 0 || x == river.width() - 1 || z == river.height() - 1;
    }

    private static TerrainRiverNetwork.NodeType nodeType(TerrainRiverTile river, int x, int z) {
        if (river.isConfluenceAt(x, z)) {
            return TerrainRiverNetwork.NodeType.CONFLUENCE;
        }
        if (river.isSourceAt(x, z)) {
            return TerrainRiverNetwork.NodeType.SOURCE;
        }
        if (river.isOutletAt(x, z)) {
            return TerrainRiverNetwork.NodeType.OUTLET;
        }
        if (isBoundaryCell(river, x, z)) {
            return TerrainRiverNetwork.NodeType.BOUNDARY;
        }
        return TerrainRiverNetwork.NodeType.INTERNAL;
    }

    private static final class VectorBuildContext {
        private final TerrainRiverTile river;
        private final boolean[] edgeVisited;
        private final Map<Integer, Integer> nodeIdsByCell = new HashMap<>();
        private final List<TerrainRiverNetwork.Node> nodes = new ArrayList<>();
        private final List<TerrainRiverNetwork.Segment> segments = new ArrayList<>();

        private VectorBuildContext(TerrainRiverTile river) {
            this.river = river;
            this.edgeVisited = new boolean[river.width() * river.height()];
        }

        private void traceFrom(int startX, int startZ) {
            if (!river.isRiverAt(startX, startZ)) {
                return;
            }

            int startIdx = river.index(startX, startZ);
            if (edgeVisited[startIdx] && !isNode(river, startX, startZ)) {
                return;
            }

            int startNodeId = ensureNode(startX, startZ);
            List<TerrainRiverNetwork.Point> points = new ArrayList<>();
            points.add(point(startX, startZ));

            int x = startX;
            int z = startZ;
            int endNodeId = -1;
            int maxSteps = river.width() * river.height();

            for (int step = 0; step < maxSteps; step++) {
                byte direction = river.directionAt(x, z);
                if (direction < 0) {
                    endNodeId = ensureNode(x, z);
                    break;
                }

                int edgeIdx = river.index(x, z);
                if (edgeVisited[edgeIdx] && step > 0) {
                    endNodeId = ensureNode(x, z);
                    break;
                }
                edgeVisited[edgeIdx] = true;

                int nx = x + DIR_X[direction];
                int nz = z + DIR_Z[direction];

                if (nx < 0 || nz < 0 || nx >= river.width() || nz >= river.height() || !river.isRiverAt(nx, nz)) {
                    endNodeId = ensureNode(x, z);
                    break;
                }

                x = nx;
                z = nz;
                points.add(point(x, z));

                if ((x != startX || z != startZ) && isNode(river, x, z)) {
                    endNodeId = ensureNode(x, z);
                    break;
                }
            }

            if (endNodeId < 0) {
                endNodeId = ensureNode(x, z);
            }

            if (endNodeId == startNodeId || points.size() < 2) {
                return;
            }

            List<TerrainRiverNetwork.Point> simplified = simplify(points, TerrainRiverVectorConfig.SIMPLIFICATION_TOLERANCE_BLOCKS);
            List<TerrainRiverNetwork.Point> smoothed = smooth(simplified, TerrainRiverVectorConfig.SMOOTHING_PASSES);
            addSegment(startNodeId, endNodeId, smoothed);
        }

        private int ensureNode(int x, int z) {
            int cellIdx = river.index(x, z);
            Integer existing = nodeIdsByCell.get(cellIdx);
            if (existing != null) {
                return existing;
            }

            int id = nodes.size();
            TerrainRiverNetwork.Node node = new TerrainRiverNetwork.Node(
                    id,
                    x,
                    z,
                    river.blockStartX() + x + 0.5D,
                    river.blockStartZ() + z + 0.5D,
                    river.surfaceYAtLocal(x, z),
                    nodeType(river, x, z),
                    river.accumulationAt(x, z),
                    river.widthBlocksAt(x, z)
            );

            nodes.add(node);
            nodeIdsByCell.put(cellIdx, id);
            return id;
        }

        private TerrainRiverNetwork.Point point(int x, int z) {
            return new TerrainRiverNetwork.Point(
                    river.blockStartX() + x + 0.5D,
                    river.blockStartZ() + z + 0.5D,
                    river.surfaceYAtLocal(x, z),
                    river.accumulationAt(x, z),
                    river.widthBlocksAt(x, z),
                    river.directionAt(x, z)
            );
        }

        private void addSegment(int startNodeId, int endNodeId, List<TerrainRiverNetwork.Point> points) {
            float sumAccumulation = 0.0F;
            float maxAccumulation = 0.0F;
            float sumWidth = 0.0F;
            float maxWidth = 0.0F;

            for (TerrainRiverNetwork.Point point : points) {
                sumAccumulation += point.accumulation();
                maxAccumulation = Math.max(maxAccumulation, point.accumulation());
                sumWidth += point.widthBlocks();
                maxWidth = Math.max(maxWidth, point.widthBlocks());
            }

            float meanAccumulation = sumAccumulation / Math.max(1, points.size());
            float meanWidth = sumWidth / Math.max(1, points.size());
            float depth = depthFromWidth(meanWidth, maxAccumulation, river.maxAccumulation());
            byte downstreamDirection = points.get(points.size() - 2).direction();

            segments.add(new TerrainRiverNetwork.Segment(
                    segments.size(),
                    startNodeId,
                    endNodeId,
                    List.copyOf(points),
                    meanAccumulation,
                    maxAccumulation,
                    meanWidth,
                    maxWidth,
                    depth,
                    downstreamDirection
            ));
        }
    }

    private static float depthFromWidth(float widthBlocks, float accumulation, float maxAccumulation) {
        float widthT = Math.max(0.0F, Math.min(1.0F, widthBlocks / 16.0F));
        float accT = logNormalized(accumulation, maxAccumulation);
        float t = Math.max(widthT, accT);
        return TerrainRiverVectorConfig.MIN_DEPTH_BLOCKS
                + (TerrainRiverVectorConfig.MAX_DEPTH_BLOCKS - TerrainRiverVectorConfig.MIN_DEPTH_BLOCKS) * t;
    }

    private static float logNormalized(float value, float maxValue) {
        double denominator = Math.log1p(Math.max(1.0F, maxValue));
        return denominator <= 0.0D
                ? 0.0F
                : clamp01((float) (Math.log1p(Math.max(0.0F, value)) / denominator));
    }

    private static List<TerrainRiverNetwork.Point> simplify(List<TerrainRiverNetwork.Point> points, float tolerance) {
        if (points.size() <= 2 || tolerance <= 0.0F) {
            return points;
        }

        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifyRange(points, 0, points.size() - 1, tolerance * tolerance, keep);

        List<TerrainRiverNetwork.Point> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    private static void simplifyRange(List<TerrainRiverNetwork.Point> points, int first, int last, float toleranceSquared, boolean[] keep) {
        if (last <= first + 1) {
            return;
        }

        double ax = points.get(first).worldX();
        double az = points.get(first).worldZ();
        double bx = points.get(last).worldX();
        double bz = points.get(last).worldZ();

        int bestIndex = -1;
        double bestDistanceSquared = -1.0D;

        for (int i = first + 1; i < last; i++) {
            double distanceSquared = pointSegmentDistanceSquared(points.get(i).worldX(), points.get(i).worldZ(), ax, az, bx, bz);
            if (distanceSquared > bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestIndex = i;
            }
        }

        if (bestIndex >= 0 && bestDistanceSquared > toleranceSquared) {
            keep[bestIndex] = true;
            simplifyRange(points, first, bestIndex, toleranceSquared, keep);
            simplifyRange(points, bestIndex, last, toleranceSquared, keep);
        }
    }

    private static double pointSegmentDistanceSquared(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax;
        double vz = bz - az;
        double wx = px - ax;
        double wz = pz - az;

        double lengthSquared = vx * vx + vz * vz;
        if (lengthSquared <= 1.0E-9D) {
            double dx = px - ax;
            double dz = pz - az;
            return dx * dx + dz * dz;
        }

        double t = Math.max(0.0D, Math.min(1.0D, (wx * vx + wz * vz) / lengthSquared));
        double cx = ax + t * vx;
        double cz = az + t * vz;
        double dx = px - cx;
        double dz = pz - cz;
        return dx * dx + dz * dz;
    }

    private static List<TerrainRiverNetwork.Point> smooth(List<TerrainRiverNetwork.Point> points, int passes) {
        if (points.size() <= 2 || passes <= 0) {
            return points;
        }

        List<TerrainRiverNetwork.Point> current = points;
        for (int pass = 0; pass < passes; pass++) {
            List<TerrainRiverNetwork.Point> next = new ArrayList<>();
            next.add(current.get(0));

            for (int i = 1; i < current.size() - 1; i++) {
                TerrainRiverNetwork.Point previous = current.get(i - 1);
                TerrainRiverNetwork.Point point = current.get(i);
                TerrainRiverNetwork.Point following = current.get(i + 1);

                double worldX = point.worldX() * 0.50D + (previous.worldX() + following.worldX()) * 0.25D;
                double worldZ = point.worldZ() * 0.50D + (previous.worldZ() + following.worldZ()) * 0.25D;

                next.add(new TerrainRiverNetwork.Point(
                        worldX,
                        worldZ,
                        point.surfaceY(),
                        point.accumulation(),
                        point.widthBlocks(),
                        point.direction()
                ));
            }

            next.add(current.get(current.size() - 1));
            current = next;
        }

        return current;
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
}
