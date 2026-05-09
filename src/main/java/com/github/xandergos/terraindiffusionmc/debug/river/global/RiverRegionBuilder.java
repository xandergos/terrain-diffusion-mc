package com.github.xandergos.terraindiffusionmc.debug.river.global;

import com.github.xandergos.terraindiffusionmc.debug.TerrainBaseTile;
import com.github.xandergos.terraindiffusionmc.debug.cost.TerrainCostBuilder;
import com.github.xandergos.terraindiffusionmc.debug.cost.TerrainCostTile;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowBuilder;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowTile;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverBuilder;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverTile;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverNetwork;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverVectorBuilder;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one global river region using the existing local debug pipeline as the backend.
 *
 * <p>This is the bridge from the current tile/halo prototype to a persistent region graph :
 * expanded terrain is generated for {@code region + halo} then the resulting vector network
 * is cropped to the interior region and converted to globally stable graph objects.</p>
 */
public final class RiverRegionBuilder implements GlobalRiverNetworkProvider.RegionBuilder {
    private static final double EPSILON = 1.0E-5D;

    private final int haloBlocks;

    public RiverRegionBuilder() {
        this(GlobalRiverNetworkProvider.DEFAULT_REGION_HALO_BLOCKS);
    }

    public RiverRegionBuilder(int haloBlocks) {
        this.haloBlocks = Math.max(0, haloBlocks);
    }

    @Override
    public RiverRegionNetwork build(RiverRegionKey key) {
        int interiorMinX = key.blockStartX();
        int interiorMinZ = key.blockStartZ();
        int interiorMaxX = key.blockEndXExclusive();
        int interiorMaxZ = key.blockEndZExclusive();

        int expandedMinX = interiorMinX - haloBlocks;
        int expandedMinZ = interiorMinZ - haloBlocks;
        int expandedMaxX = interiorMaxX + haloBlocks;
        int expandedMaxZ = interiorMaxZ + haloBlocks;
        int expandedWidth = expandedMaxX - expandedMinX;
        int expandedHeight = expandedMaxZ - expandedMinZ;

        TerrainBaseTile terrain = LocalTerrainProvider.getInstance().fetchTerrainBaseTile(
                expandedMinZ,
                expandedMinX,
                expandedMaxZ,
                expandedMaxX
        );

        TerrainCostTile cost = TerrainCostBuilder.buildCropped(
                terrain,
                expandedMinX,
                expandedMinZ,
                expandedWidth,
                expandedHeight
        );

        TerrainFlowTile flow = TerrainFlowBuilder.buildCropped(
                terrain,
                cost,
                expandedMinX,
                expandedMinZ,
                expandedWidth,
                expandedHeight
        );

        TerrainRiverTile river = TerrainRiverBuilder.build(flow);
        TerrainRiverNetwork localVectorNetwork = TerrainRiverVectorBuilder.build(river);
        RiverNetwork globalNetwork = convertCropped(key, localVectorNetwork, interiorMinX, interiorMinZ, interiorMaxX, interiorMaxZ);

        return new RiverRegionNetwork(key, haloBlocks, globalNetwork);
    }

    private static RiverNetwork convertCropped(
            RiverRegionKey key,
            TerrainRiverNetwork local,
            double minX,
            double minZ,
            double maxX,
            double maxZ
    ) {
        RiverNetwork.Builder builder = RiverNetwork.builder();
        Map<Integer, TerrainRiverNetwork.Node> localNodes = new HashMap<>();
        Map<Long, RiverNode> nodesById = new LinkedHashMap<>();

        for (TerrainRiverNetwork.Node node : local.nodes()) {
            localNodes.put(node.id(), node);
        }

        for (TerrainRiverNetwork.Segment segment : local.segments()) {
            List<TerrainRiverNetwork.Point> clipped = clipPolyline(segment.points(), minX, minZ, maxX, maxZ);
            if (clipped.size() < 2) {
                continue;
            }

            TerrainRiverNetwork.Node localStartNode = localNodes.get(segment.startNodeId());
            TerrainRiverNetwork.Node localEndNode = localNodes.get(segment.endNodeId());

            RiverNode startNode = createNode(key.seed(), clipped.get(0), localStartNode, minX, minZ, maxX, maxZ);
            RiverNode endNode = createNode(key.seed(), clipped.get(clipped.size() - 1), localEndNode, minX, minZ, maxX, maxZ);

            nodesById.putIfAbsent(startNode.id(), startNode);
            nodesById.putIfAbsent(endNode.id(), endNode);

            List<RiverPoint> points = convertPoints(clipped, segment.depthBlocks());
            int pathSignature = RiverNetwork.pathSignature(points);
            long segmentId = RiverNetwork.deterministicSegmentId(key.seed(), startNode.id(), endNode.id(), pathSignature);

            RiverSegment globalSegment = new RiverSegment(
                    segmentId,
                    startNode.id(),
                    endNode.id(),
                    points,
                    segment.meanAccumulation(),
                    segment.maxAccumulation(),
                    dischargeFromAccumulation(segment.maxAccumulation()),
                    segment.meanWidthBlocks(),
                    segment.maxWidthBlocks(),
                    segment.depthBlocks(),
                    segment.downstreamDirection(),
                    null
            );

            builder.addNode(startNode);
            builder.addNode(endNode);
            builder.addSegment(globalSegment);
        }

        // Preserve any coincident nodes that were de-duplicated by deterministic ID.
        for (RiverNode node : nodesById.values()) {
            builder.addNode(node);
        }

        return builder.build();
    }

    private static RiverNode createNode(
            long seed,
            TerrainRiverNetwork.Point point,
            TerrainRiverNetwork.Node localNode,
            double minX,
            double minZ,
            double maxX,
            double maxZ
    ) {
        RiverNode.NodeType type = nodeType(localNode, point, minX, minZ, maxX, maxZ);
        long id = RiverNetwork.deterministicNodeId(seed, point.worldX(), point.worldZ(), type);
        float accumulation = point.accumulation();
        float width = point.widthBlocks();

        if (localNode != null && closeEnough(localNode.worldX(), localNode.worldZ(), point.worldX(), point.worldZ())) {
            accumulation = localNode.accumulation();
            width = localNode.widthBlocks();
        }

        return new RiverNode(
                id,
                point.worldX(),
                point.worldZ(),
                point.surfaceY(),
                type,
                accumulation,
                dischargeFromAccumulation(accumulation),
                width,
                List.of(),
                List.of()
        );
    }

    private static RiverNode.NodeType nodeType(
            TerrainRiverNetwork.Node localNode,
            TerrainRiverNetwork.Point point,
            double minX,
            double minZ,
            double maxX,
            double maxZ
    ) {
        if (onBoundary(point.worldX(), point.worldZ(), minX, minZ, maxX, maxZ)) {
            return RiverNode.NodeType.BOUNDARY;
        }

        if (localNode == null || !closeEnough(localNode.worldX(), localNode.worldZ(), point.worldX(), point.worldZ())) {
            return RiverNode.NodeType.INTERNAL;
        }

        return switch (localNode.type()) {
            case SOURCE -> RiverNode.NodeType.SOURCE;
            case CONFLUENCE -> RiverNode.NodeType.CONFLUENCE;
            case OUTLET -> RiverNode.NodeType.OUTLET;
            case BOUNDARY -> RiverNode.NodeType.BOUNDARY;
            case INTERNAL -> RiverNode.NodeType.INTERNAL;
        };
    }

    private static List<RiverPoint> convertPoints(List<TerrainRiverNetwork.Point> points, float depthBlocks) {
        List<RiverPoint> result = new ArrayList<>(points.size());
        for (TerrainRiverNetwork.Point point : points) {
            result.add(new RiverPoint(
                    point.worldX(),
                    point.worldZ(),
                    point.surfaceY(),
                    point.accumulation(),
                    dischargeFromAccumulation(point.accumulation()),
                    point.widthBlocks(),
                    depthBlocks,
                    point.direction()
            ));
        }
        return result;
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

            addIfDistinct(result, clipped.start());
            addIfDistinct(result, clipped.end());
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

        return new TerrainRiverNetwork.Point(
                x,
                z,
                surfaceY,
                accumulation,
                width,
                a.direction()
        );
    }

    private static void addIfDistinct(List<TerrainRiverNetwork.Point> result, TerrainRiverNetwork.Point point) {
        if (result.isEmpty()) {
            result.add(point);
            return;
        }

        TerrainRiverNetwork.Point previous = result.get(result.size() - 1);
        if (!closeEnough(previous.worldX(), previous.worldZ(), point.worldX(), point.worldZ())) {
            result.add(point);
        }
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

    private static float dischargeFromAccumulation(float accumulation) {
        // Placeholder hydrology : stable monotonic proxy. v25 will replace this with
        // upstream-propagated discharge after cross-region stitching is available.
        return Math.max(0.0F, accumulation);
    }

    private record ClippedSegment(TerrainRiverNetwork.Point start, TerrainRiverNetwork.Point end) {
    }
}
