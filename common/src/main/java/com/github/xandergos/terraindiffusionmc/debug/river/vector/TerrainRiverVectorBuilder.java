package com.github.xandergos.terraindiffusionmc.debug.river.vector;

import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverConfig;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverTile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TerrainRiverVectorBuilder {
    private static final int[] DIR_X = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DIR_Z = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final double EPSILON = 1.0E-5D;

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

        TerrainRiverNetwork raw = new TerrainRiverNetwork(
                river.blockStartX(),
                river.blockStartZ(),
                river.width(),
                river.height(),
                List.copyOf(context.nodes),
                List.copyOf(context.segments),
                river.maxAccumulation()
        );
        return mergeNearbyParallelSegments(raw);
    }

    public static TerrainRiverNetwork buildCropped(
            TerrainRiverTile river,
            int blockStartX,
            int blockStartZ,
            int width,
            int height
    ) {
        TerrainRiverNetwork expanded = build(river);
        return crop(expanded, blockStartX, blockStartZ, width, height);
    }

    public static TerrainRiverNetwork crop(
            TerrainRiverNetwork network,
            int blockStartX,
            int blockStartZ,
            int width,
            int height
    ) {
        double minX = blockStartX;
        double minZ = blockStartZ;
        double maxX = blockStartX + width;
        double maxZ = blockStartZ + height;
        CroppedNetworkBuilder builder = new CroppedNetworkBuilder(network, blockStartX, blockStartZ, width, height);

        for (TerrainRiverNetwork.Segment segment : network.segments()) {
            List<TerrainRiverNetwork.Point> clipped = clipPolyline(segment.points(), minX, minZ, maxX, maxZ);
            if (clipped.size() < 2) {
                continue;
            }

            int startNodeId = builder.ensureNode(clipped.get(0), nodeTypeForCroppedEndpoint(segment, network, clipped.get(0), true, minX, minZ, maxX, maxZ));
            int endNodeId = builder.ensureNode(clipped.get(clipped.size() - 1), nodeTypeForCroppedEndpoint(segment, network, clipped.get(clipped.size() - 1), false, minX, minZ, maxX, maxZ));
            if (startNodeId != endNodeId) {
                builder.addSegment(startNodeId, endNodeId, clipped, segment.downstreamDirection());
            }
        }

        return builder.build();
    }

    private static TerrainRiverNetwork.NodeType nodeTypeForCroppedEndpoint(
            TerrainRiverNetwork.Segment segment,
            TerrainRiverNetwork network,
            TerrainRiverNetwork.Point point,
            boolean start,
            double minX,
            double minZ,
            double maxX,
            double maxZ
    ) {
        if (onBoundary(point.worldX(), point.worldZ(), minX, minZ, maxX, maxZ)) {
            return TerrainRiverNetwork.NodeType.BOUNDARY;
        }

        TerrainRiverNetwork.Node original = network.nodes().stream()
                .filter(node -> node.id() == (start ? segment.startNodeId() : segment.endNodeId()))
                .findFirst()
                .orElse(null);
        if (original != null && closeEnough(original.worldX(), original.worldZ(), point.worldX(), point.worldZ())) {
            return original.type();
        }
        return TerrainRiverNetwork.NodeType.INTERNAL;
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
            List<TerrainRiverNetwork.Point> anchored = preserveTerrainAnchors(points, simplified, TerrainRiverVectorConfig.MAX_VECTOR_SEGMENT_LENGTH_BLOCKS);
            List<TerrainRiverNetwork.Point> smoothed = constrainedSmooth(anchored, TerrainRiverVectorConfig.SMOOTHING_PASSES);
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
            byte direction = river.directionAt(x, z);
            double baseWorldX = river.blockStartX() + x + 0.5D;
            double baseWorldZ = river.blockStartZ() + z + 0.5D;
            double worldX = baseWorldX;
            double worldZ = baseWorldZ;

            if (direction >= 0) {
                double wave = Math.sin((baseWorldX * 1.37D + baseWorldZ * 0.91D) / TerrainRiverConfig.MEANDER_WAVELENGTH_BLOCKS);
                double offset = wave
                        * river.meanderStrengthAt(x, z)
                        * river.widthBlocksAt(x, z)
                        * TerrainRiverConfig.MAX_MEANDER_OFFSET_FRACTION_OF_WIDTH;
                double perpX = -DIR_Z[direction];
                double perpZ = DIR_X[direction];
                double length = Math.sqrt(perpX * perpX + perpZ * perpZ);
                if (length > 1.0E-6D) {
                    double candidateX = baseWorldX + offset * perpX / length;
                    double candidateZ = baseWorldZ + offset * perpZ / length;
                    if (isTerrainCompatibleOffset(x, z, candidateX, candidateZ)) {
                        worldX = candidateX;
                        worldZ = candidateZ;
                    }
                }
            }

            return new TerrainRiverNetwork.Point(
                    worldX,
                    worldZ,
                    river.surfaceYAtLocal(x, z),
                    river.accumulationAt(x, z),
                    river.widthBlocksAt(x, z),
                    river.depthBlocksAt(x, z),
                    direction
            );
        }

        private boolean isTerrainCompatibleOffset(int sourceX, int sourceZ, double worldX, double worldZ) {
            int localX = (int) Math.floor(worldX - river.blockStartX());
            int localZ = (int) Math.floor(worldZ - river.blockStartZ());
            if (localX < 0 || localZ < 0 || localX >= river.width() || localZ >= river.height()) {
                return false;
            }

            int sourceY = river.surfaceYAtLocal(sourceX, sourceZ);
            int candidateY = river.surfaceYAtLocal(localX, localZ);
            if (candidateY > sourceY + TerrainRiverVectorConfig.MAX_SMOOTHING_TERRAIN_RISE_BLOCKS) {
                return false;
            }

            int radius = Math.max(1, (int) Math.ceil(river.widthBlocksAt(sourceX, sourceZ) * 0.35F));
            return nearestRiverDistanceSquared(localX, localZ, radius) <= radius * radius;
        }

        private int nearestRiverDistanceSquared(int localX, int localZ, int radius) {
            int best = Integer.MAX_VALUE;
            for (int dz = -radius; dz <= radius; dz++) {
                int z = localZ + dz;
                if (z < 0 || z >= river.height()) {
                    continue;
                }
                for (int dx = -radius; dx <= radius; dx++) {
                    int x = localX + dx;
                    if (x < 0 || x >= river.width() || !river.isRiverAt(x, z)) {
                        continue;
                    }
                    best = Math.min(best, dx * dx + dz * dz);
                }
            }
            return best;
        }

        private List<TerrainRiverNetwork.Point> constrainedSmooth(List<TerrainRiverNetwork.Point> points, int passes) {
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

                    double candidateX = point.worldX() * 0.50D + (previous.worldX() + following.worldX()) * 0.25D;
                    double candidateZ = point.worldZ() * 0.50D + (previous.worldZ() + following.worldZ()) * 0.25D;
                    next.add(constrainPointToTerrain(point, candidateX, candidateZ));
                }

                next.add(current.get(current.size() - 1));
                current = next;
            }

            return current;
        }

        private TerrainRiverNetwork.Point constrainPointToTerrain(TerrainRiverNetwork.Point point, double candidateX, double candidateZ) {
            double maxMove = Math.max(1.50D, point.widthBlocks() * TerrainRiverVectorConfig.MAX_SMOOTHING_OFFSET_FRACTION_OF_WIDTH);
            double dx = candidateX - point.worldX();
            double dz = candidateZ - point.worldZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > maxMove && distance > 1.0E-6D) {
                double scale = maxMove / distance;
                candidateX = point.worldX() + dx * scale;
                candidateZ = point.worldZ() + dz * scale;
            }

            int localX = (int) Math.floor(candidateX - river.blockStartX());
            int localZ = (int) Math.floor(candidateZ - river.blockStartZ());
            if (localX < 0 || localZ < 0 || localX >= river.width() || localZ >= river.height()) {
                return point;
            }

            int candidateY = river.surfaceYAtLocal(localX, localZ);
            if (candidateY > point.surfaceY() + TerrainRiverVectorConfig.MAX_SMOOTHING_TERRAIN_RISE_BLOCKS) {
                return point;
            }

            int radius = Math.max(1, (int) Math.ceil(point.widthBlocks() * 0.35F));
            if (nearestRiverDistanceSquared(localX, localZ, radius) > radius * radius) {
                return point;
            }

            return new TerrainRiverNetwork.Point(
                    candidateX,
                    candidateZ,
                    Math.min(point.surfaceY(), candidateY),
                    point.accumulation(),
                    point.widthBlocks(),
                    point.depthBlocks(),
                    point.direction()
            );
        }

        private void addSegment(int startNodeId, int endNodeId, List<TerrainRiverNetwork.Point> points) {
            SegmentStats stats = SegmentStats.from(points);
            byte downstreamDirection = points.get(Math.max(0, points.size() - 2)).direction();

            segments.add(new TerrainRiverNetwork.Segment(
                    segments.size(),
                    startNodeId,
                    endNodeId,
                    List.copyOf(points),
                    stats.meanAccumulation(),
                    stats.maxAccumulation(),
                    stats.meanWidthBlocks(),
                    stats.maxWidthBlocks(),
                    stats.depthBlocks(),
                    downstreamDirection
            ));
        }
    }


    private static TerrainRiverNetwork mergeNearbyParallelSegments(TerrainRiverNetwork network) {
        if (network.segments().size() <= 1) {
            return network;
        }

        boolean[] removed = new boolean[network.segments().size()];
        for (int i = 0; i < network.segments().size(); i++) {
            if (removed[i]) {
                continue;
            }

            TerrainRiverNetwork.Segment a = network.segments().get(i);
            for (int j = i + 1; j < network.segments().size(); j++) {
                if (removed[j]) {
                    continue;
                }

                TerrainRiverNetwork.Segment b = network.segments().get(j);
                int strongerIndex = strongerSegmentIndex(a, b) == 0 ? i : j;
                int weakerIndex = strongerIndex == i ? j : i;
                TerrainRiverNetwork.Segment stronger = network.segments().get(strongerIndex);
                TerrainRiverNetwork.Segment weaker = network.segments().get(weakerIndex);

                if (shouldCollapseParallelSegment(stronger, weaker)) {
                    removed[weakerIndex] = true;
                }
            }
        }

        List<TerrainRiverNetwork.Segment> kept = new ArrayList<>();
        for (int i = 0; i < network.segments().size(); i++) {
            if (removed[i]) {
                continue;
            }

            TerrainRiverNetwork.Segment segment = network.segments().get(i);
            kept.add(new TerrainRiverNetwork.Segment(
                    kept.size(),
                    segment.startNodeId(),
                    segment.endNodeId(),
                    segment.points(),
                    segment.meanAccumulation(),
                    segment.maxAccumulation(),
                    segment.meanWidthBlocks(),
                    segment.maxWidthBlocks(),
                    segment.depthBlocks(),
                    segment.downstreamDirection()
            ));
        }

        return new TerrainRiverNetwork(
                network.blockStartX(),
                network.blockStartZ(),
                network.width(),
                network.height(),
                network.nodes(),
                List.copyOf(kept),
                network.maxAccumulation()
        );
    }

    private static int strongerSegmentIndex(TerrainRiverNetwork.Segment a, TerrainRiverNetwork.Segment b) {
        int byAccumulation = Float.compare(a.maxAccumulation(), b.maxAccumulation());
        if (Math.abs(a.maxAccumulation() - b.maxAccumulation()) > 1.0E-3F) {
            return byAccumulation >= 0 ? 0 : 1;
        }

        int byWidth = Float.compare(a.maxWidthBlocks(), b.maxWidthBlocks());
        if (Math.abs(a.maxWidthBlocks() - b.maxWidthBlocks()) > 1.0E-3F) {
            return byWidth >= 0 ? 0 : 1;
        }

        return pathLength(a.points()) >= pathLength(b.points()) ? 0 : 1;
    }

    private static boolean shouldCollapseParallelSegment(TerrainRiverNetwork.Segment stronger, TerrainRiverNetwork.Segment weaker) {
        if (stronger.points().size() < 2 || weaker.points().size() < 2) {
            return false;
        }

        if (weaker.maxAccumulation() > stronger.maxAccumulation() * TerrainRiverVectorConfig.PARALLEL_MERGE_MAX_RELATIVE_DISCHARGE) {
            return false;
        }

        double dot = directionDot(stronger.points(), weaker.points());
        if (dot < TerrainRiverVectorConfig.PARALLEL_MERGE_MIN_DIRECTION_DOT) {
            return false;
        }

        double radius = TerrainRiverVectorConfig.PARALLEL_MERGE_RADIUS_BLOCKS
                + Math.min(stronger.maxWidthBlocks(), weaker.maxWidthBlocks()) * 0.25D;
        double radiusSquared = radius * radius;
        double endpointRadius = radius * TerrainRiverVectorConfig.PARALLEL_MERGE_ENDPOINT_RADIUS_MULTIPLIER;
        double endpointRadiusSquared = endpointRadius * endpointRadius;

        NearestPoint startNearest = nearestPointOnPolyline(weaker.points().get(0), stronger.points());
        NearestPoint endNearest = nearestPointOnPolyline(weaker.points().get(weaker.points().size() - 1), stronger.points());
        if (startNearest.distanceSquared() > endpointRadiusSquared || endNearest.distanceSquared() > endpointRadiusSquared) {
            return false;
        }

        int near = 0;
        int checked = 0;
        int verticalDeltaSum = 0;

        for (TerrainRiverNetwork.Point point : weaker.points()) {
            NearestPoint nearest = nearestPointOnPolyline(point, stronger.points());
            if (nearest.distanceSquared() <= radiusSquared) {
                near++;
                verticalDeltaSum += Math.abs(point.surfaceY() - nearest.surfaceY());
            }
            checked++;
        }

        if (checked == 0) {
            return false;
        }

        float nearFraction = near / (float) checked;
        if (nearFraction < TerrainRiverVectorConfig.PARALLEL_MERGE_MIN_NEAR_FRACTION) {
            return false;
        }

        float meanVerticalDelta = near == 0 ? Float.MAX_VALUE : verticalDeltaSum / (float) near;
        return meanVerticalDelta <= TerrainRiverVectorConfig.PARALLEL_MERGE_MAX_VERTICAL_DELTA_BLOCKS;
    }

    private static double directionDot(List<TerrainRiverNetwork.Point> a, List<TerrainRiverNetwork.Point> b) {
        TerrainRiverNetwork.Point a0 = a.get(0);
        TerrainRiverNetwork.Point a1 = a.get(a.size() - 1);
        TerrainRiverNetwork.Point b0 = b.get(0);
        TerrainRiverNetwork.Point b1 = b.get(b.size() - 1);

        double ax = a1.worldX() - a0.worldX();
        double az = a1.worldZ() - a0.worldZ();
        double bx = b1.worldX() - b0.worldX();
        double bz = b1.worldZ() - b0.worldZ();
        double al = Math.sqrt(ax * ax + az * az);
        double bl = Math.sqrt(bx * bx + bz * bz);
        if (al <= 1.0E-6D || bl <= 1.0E-6D) {
            return 0.0D;
        }

        return (ax * bx + az * bz) / (al * bl);
    }

    private static NearestPoint nearestPointOnPolyline(TerrainRiverNetwork.Point point, List<TerrainRiverNetwork.Point> polyline) {
        double bestDistanceSquared = Double.MAX_VALUE;
        int bestSurfaceY = point.surfaceY();

        for (int i = 0; i < polyline.size() - 1; i++) {
            TerrainRiverNetwork.Point a = polyline.get(i);
            TerrainRiverNetwork.Point b = polyline.get(i + 1);
            Projection projection = projectToSegment(point.worldX(), point.worldZ(), a, b);
            if (projection.distanceSquared() < bestDistanceSquared) {
                bestDistanceSquared = projection.distanceSquared();
                bestSurfaceY = projection.surfaceY();
            }
        }

        return new NearestPoint(bestDistanceSquared, bestSurfaceY);
    }

    private static Projection projectToSegment(double px, double pz, TerrainRiverNetwork.Point a, TerrainRiverNetwork.Point b) {
        double vx = b.worldX() - a.worldX();
        double vz = b.worldZ() - a.worldZ();
        double wx = px - a.worldX();
        double wz = pz - a.worldZ();
        double lengthSquared = vx * vx + vz * vz;
        double t = lengthSquared <= 1.0E-9D ? 0.0D : Math.max(0.0D, Math.min(1.0D, (wx * vx + wz * vz) / lengthSquared));
        double cx = a.worldX() + vx * t;
        double cz = a.worldZ() + vz * t;
        double dx = px - cx;
        double dz = pz - cz;
        int surfaceY = (int) Math.round(a.surfaceY() + (b.surfaceY() - a.surfaceY()) * t);
        return new Projection(dx * dx + dz * dz, surfaceY);
    }

    private static final class CroppedNetworkBuilder {
        private final TerrainRiverNetwork source;
        private final int blockStartX;
        private final int blockStartZ;
        private final int width;
        private final int height;
        private final Map<CroppedNodeKey, Integer> nodeIdsByKey = new HashMap<>();
        private final List<TerrainRiverNetwork.Node> nodes = new ArrayList<>();
        private final List<TerrainRiverNetwork.Segment> segments = new ArrayList<>();

        private CroppedNetworkBuilder(TerrainRiverNetwork source, int blockStartX, int blockStartZ, int width, int height) {
            this.source = source;
            this.blockStartX = blockStartX;
            this.blockStartZ = blockStartZ;
            this.width = width;
            this.height = height;
        }

        private int ensureNode(TerrainRiverNetwork.Point point, TerrainRiverNetwork.NodeType type) {
            CroppedNodeKey key = CroppedNodeKey.from(point);
            Integer existing = nodeIdsByKey.get(key);
            if (existing != null) {
                return existing;
            }

            int id = nodes.size();
            int localX = clamp((int) Math.floor(point.worldX() - blockStartX), 0, Math.max(0, width - 1));
            int localZ = clamp((int) Math.floor(point.worldZ() - blockStartZ), 0, Math.max(0, height - 1));
            nodes.add(new TerrainRiverNetwork.Node(
                    id,
                    localX,
                    localZ,
                    point.worldX(),
                    point.worldZ(),
                    point.surfaceY(),
                    type,
                    point.accumulation(),
                    point.widthBlocks()
            ));
            nodeIdsByKey.put(key, id);
            return id;
        }

        private void addSegment(int startNodeId, int endNodeId, List<TerrainRiverNetwork.Point> points, byte downstreamDirection) {
            SegmentStats stats = SegmentStats.from(points);
            segments.add(new TerrainRiverNetwork.Segment(
                    segments.size(),
                    startNodeId,
                    endNodeId,
                    List.copyOf(points),
                    stats.meanAccumulation(),
                    stats.maxAccumulation(),
                    stats.meanWidthBlocks(),
                    stats.maxWidthBlocks(),
                    stats.depthBlocks(),
                    downstreamDirection
            ));
        }

        private TerrainRiverNetwork build() {
            return new TerrainRiverNetwork(
                    blockStartX,
                    blockStartZ,
                    width,
                    height,
                    List.copyOf(nodes),
                    List.copyOf(segments),
                    source.maxAccumulation()
            );
        }
    }

    private record CroppedNodeKey(long qx, long qz) {
        private static CroppedNodeKey from(TerrainRiverNetwork.Point point) {
            return new CroppedNodeKey(
                    Math.round(point.worldX() * 8.0D),
                    Math.round(point.worldZ() * 8.0D)
            );
        }
    }

    private record SegmentStats(
            float meanAccumulation,
            float maxAccumulation,
            float meanWidthBlocks,
            float maxWidthBlocks,
            float depthBlocks
    ) {
        private static SegmentStats from(List<TerrainRiverNetwork.Point> points) {
            float sumAccumulation = 0.0F;
            float maxAccumulation = 0.0F;
            float sumWidth = 0.0F;
            float maxWidth = 0.0F;
            float sumDepth = 0.0F;
            float maxDepth = 0.0F;

            for (TerrainRiverNetwork.Point point : points) {
                sumAccumulation += point.accumulation();
                maxAccumulation = Math.max(maxAccumulation, point.accumulation());
                sumWidth += point.widthBlocks();
                maxWidth = Math.max(maxWidth, point.widthBlocks());
                sumDepth += point.depthBlocks();
                maxDepth = Math.max(maxDepth, point.depthBlocks());
            }

            int count = Math.max(1, points.size());
            float depth = Math.max(sumDepth / count, maxDepth * 0.82F);
            return new SegmentStats(
                    sumAccumulation / count,
                    maxAccumulation,
                    sumWidth / count,
                    maxWidth,
                    depth
            );
        }
    }

    private static List<TerrainRiverNetwork.Point> preserveTerrainAnchors(
            List<TerrainRiverNetwork.Point> raw,
            List<TerrainRiverNetwork.Point> simplified,
            float maxSegmentLength
    ) {
        if (raw.size() <= 2 || simplified.size() <= 1 || maxSegmentLength <= 0.0F) {
            return simplified;
        }

        Map<TerrainRiverNetwork.Point, Integer> rawIndexByPoint = new HashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            rawIndexByPoint.put(raw.get(i), i);
        }

        List<TerrainRiverNetwork.Point> result = new ArrayList<>();
        result.add(simplified.get(0));

        for (int i = 0; i < simplified.size() - 1; i++) {
            TerrainRiverNetwork.Point a = simplified.get(i);
            TerrainRiverNetwork.Point b = simplified.get(i + 1);
            int ai = rawIndexByPoint.getOrDefault(a, -1);
            int bi = rawIndexByPoint.getOrDefault(b, -1);
            if (ai >= 0 && bi > ai) {
                double accumulated = 0.0D;
                TerrainRiverNetwork.Point previous = raw.get(ai);
                for (int j = ai + 1; j < bi; j++) {
                    TerrainRiverNetwork.Point candidate = raw.get(j);
                    accumulated += distance(previous, candidate);
                    if (accumulated >= maxSegmentLength) {
                        addDistinct(result, candidate);
                        accumulated = 0.0D;
                    }
                    previous = candidate;
                }
            }
            addDistinct(result, b);
        }

        return result;
    }

    private static List<TerrainRiverNetwork.Point> simplify(List<TerrainRiverNetwork.Point> points, float tolerance) {
        if (points.size() <= 2 || tolerance <= 0.0F) {
            return points;
        }

        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifyRange(points, 0, points.size() - 1, tolerance * tolerance, keep);
        preserveMaxSegmentLength(points, keep, TerrainRiverVectorConfig.MAX_VECTOR_SEGMENT_LENGTH_BLOCKS);

        List<TerrainRiverNetwork.Point> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    private static void preserveMaxSegmentLength(List<TerrainRiverNetwork.Point> points, boolean[] keep, float maxSegmentLength) {
        if (maxSegmentLength <= 0.0F) {
            return;
        }

        int lastKept = 0;
        for (int i = 1; i < points.size(); i++) {
            if (!keep[i]) {
                continue;
            }

            double pathLength = 0.0D;
            TerrainRiverNetwork.Point previous = points.get(lastKept);
            for (int j = lastKept + 1; j <= i; j++) {
                TerrainRiverNetwork.Point current = points.get(j);
                pathLength += distance(previous, current);
                if (pathLength >= maxSegmentLength && j < i) {
                    keep[j] = true;
                    pathLength = 0.0D;
                }
                previous = current;
            }
            lastKept = i;
        }
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

    private static List<TerrainRiverNetwork.Point> clipPolyline(
            List<TerrainRiverNetwork.Point> points,
            double minX,
            double minZ,
            double maxX,
            double maxZ
    ) {
        List<TerrainRiverNetwork.Point> result = new ArrayList<>();

        for (int i = 0; i < points.size() - 1; i++) {
            TerrainRiverNetwork.Point a = points.get(i);
            TerrainRiverNetwork.Point b = points.get(i + 1);
            ClippedSegment clipped = clipSegment(a, b, minX, minZ, maxX, maxZ);
            if (clipped == null) {
                continue;
            }

            addDistinct(result, clipped.start());
            addDistinct(result, clipped.end());
        }

        return result;
    }

    private static ClippedSegment clipSegment(
            TerrainRiverNetwork.Point a,
            TerrainRiverNetwork.Point b,
            double minX,
            double minZ,
            double maxX,
            double maxZ
    ) {
        double dx = b.worldX() - a.worldX();
        double dz = b.worldZ() - a.worldZ();
        double t0 = 0.0D;
        double t1 = 1.0D;

        double[] p = {-dx, dx, -dz, dz};
        double[] q = {a.worldX() - minX, maxX - a.worldX(), a.worldZ() - minZ, maxZ - a.worldZ()};

        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1.0E-12D) {
                if (q[i] < 0.0D) {
                    return null;
                }
            } else {
                double r = q[i] / p[i];
                if (p[i] < 0.0D) {
                    if (r > t1) {
                        return null;
                    }
                    if (r > t0) {
                        t0 = r;
                    }
                } else {
                    if (r < t0) {
                        return null;
                    }
                    if (r < t1) {
                        t1 = r;
                    }
                }
            }
        }

        if (t1 < t0) {
            return null;
        }

        return new ClippedSegment(interpolate(a, b, t0), interpolate(a, b, t1));
    }

    private static TerrainRiverNetwork.Point interpolate(TerrainRiverNetwork.Point a, TerrainRiverNetwork.Point b, double t) {
        if (t <= EPSILON) {
            return a;
        }
        if (t >= 1.0D - EPSILON) {
            return b;
        }

        double x = a.worldX() + (b.worldX() - a.worldX()) * t;
        double z = a.worldZ() + (b.worldZ() - a.worldZ()) * t;
        int surfaceY = (int) Math.round(a.surfaceY() + (b.surfaceY() - a.surfaceY()) * t);
        float accumulation = (float) (a.accumulation() + (b.accumulation() - a.accumulation()) * t);
        float width = (float) (a.widthBlocks() + (b.widthBlocks() - a.widthBlocks()) * t);
        float depth = (float) (a.depthBlocks() + (b.depthBlocks() - a.depthBlocks()) * t);

        return new TerrainRiverNetwork.Point(
                x,
                z,
                surfaceY,
                accumulation,
                width,
                depth,
                a.direction()
        );
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

    private static boolean onBoundary(double x, double z, double minX, double minZ, double maxX, double maxZ) {
        return Math.abs(x - minX) <= 0.75D
                || Math.abs(z - minZ) <= 0.75D
                || Math.abs(x - maxX) <= 0.75D
                || Math.abs(z - maxZ) <= 0.75D;
    }

    private static boolean closeEnough(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz <= 1.0E-4D;
    }

    private static void addDistinct(List<TerrainRiverNetwork.Point> result, TerrainRiverNetwork.Point point) {
        if (result.isEmpty()) {
            result.add(point);
            return;
        }

        TerrainRiverNetwork.Point previous = result.get(result.size() - 1);
        if (!closeEnough(previous.worldX(), previous.worldZ(), point.worldX(), point.worldZ())) {
            result.add(point);
        }
    }

    private static double pathLength(List<TerrainRiverNetwork.Point> points) {
        double result = 0.0D;
        for (int i = 0; i < points.size() - 1; i++) {
            result += distance(points.get(i), points.get(i + 1));
        }
        return result;
    }

    private static double distance(TerrainRiverNetwork.Point a, TerrainRiverNetwork.Point b) {
        double dx = a.worldX() - b.worldX();
        double dz = a.worldZ() - b.worldZ();
        return Math.sqrt(dx * dx + dz * dz);
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record NearestPoint(double distanceSquared, int surfaceY) {
    }

    private record Projection(double distanceSquared, int surfaceY) {
    }

    private record ClippedSegment(TerrainRiverNetwork.Point start, TerrainRiverNetwork.Point end) {
    }
}
