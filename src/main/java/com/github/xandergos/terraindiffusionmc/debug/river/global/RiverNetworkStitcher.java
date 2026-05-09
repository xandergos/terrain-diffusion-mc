package com.github.xandergos.terraindiffusionmc.debug.river.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stitches region-local river graphs into one temporary global graph.
 *
 * <p>The stitcher intentionally does not regenerate hydrology. It only performs
 * the graph operation : border node matching, segment reconnection and duplicate
 * segment suppression. Discharge/width/depth recalculation is handled by
 * {@link RiverNetworkHydrology}.</p>
 */
public final class RiverNetworkStitcher {
    private static final double BORDER_MATCH_GRID = 2.0D;

    private RiverNetworkStitcher() {
    }

    public static RiverNetwork stitch(Collection<RiverRegionNetwork> regions, long seed) {
        StitchContext context = new StitchContext(seed);

        for (RiverRegionNetwork region : regions) {
            for (RiverNode node : region.network().nodes()) {
                context.registerNode(region, node);
            }
        }

        for (RiverRegionNetwork region : regions) {
            for (RiverSegment segment : region.network().segments()) {
                context.registerSegment(segment);
            }
        }

        return context.build();
    }

    private static final class StitchContext {
        private final long seed;
        private final Map<Long, Long> originalToCanonicalNodeId = new HashMap<>();
        private final Map<NodeMergeKey, NodeAccumulator> mergeNodes = new LinkedHashMap<>();
        private final Map<Long, RiverNode> nonMergedNodes = new LinkedHashMap<>();
        private final Map<Long, RiverSegment> segmentsById = new LinkedHashMap<>();

        private StitchContext(long seed) {
            this.seed = seed;
        }

        private void registerNode(RiverRegionNetwork region, RiverNode node) {
            if (shouldMerge(region, node)) {
                NodeMergeKey key = NodeMergeKey.from(node);
                NodeAccumulator acc = mergeNodes.computeIfAbsent(key, ignored -> new NodeAccumulator(seed, key));
                acc.add(node);
                originalToCanonicalNodeId.put(node.id(), acc.canonicalId());
            } else {
                nonMergedNodes.putIfAbsent(node.id(), node);
                originalToCanonicalNodeId.put(node.id(), node.id());
            }
        }

        private boolean shouldMerge(RiverRegionNetwork region, RiverNode node) {
            if (node.type() == RiverNode.NodeType.BOUNDARY) {
                return true;
            }

            double x = node.worldX();
            double z = node.worldZ();
            double tolerance = BORDER_MATCH_GRID;
            return Math.abs(x - region.interiorMinX()) <= tolerance
                    || Math.abs(x - region.interiorMaxXExclusive()) <= tolerance
                    || Math.abs(z - region.interiorMinZ()) <= tolerance
                    || Math.abs(z - region.interiorMaxZExclusive()) <= tolerance;
        }

        private void registerSegment(RiverSegment segment) {
            long startId = originalToCanonicalNodeId.getOrDefault(segment.startNodeId(), segment.startNodeId());
            long endId = originalToCanonicalNodeId.getOrDefault(segment.endNodeId(), segment.endNodeId());

            if (startId == endId || segment.polyline().size() < 2) {
                return;
            }

            List<RiverPoint> points = segment.polyline();
            int signature = RiverNetwork.pathSignature(points);
            long id = RiverNetwork.deterministicSegmentId(seed, startId, endId, signature);

            RiverSegment stitched = new RiverSegment(
                    id,
                    startId,
                    endId,
                    points,
                    segment.meanAccumulation(),
                    segment.maxAccumulation(),
                    segment.discharge(),
                    segment.meanWidthBlocks(),
                    segment.maxWidthBlocks(),
                    segment.depthBlocks(),
                    segment.downstreamDirection(),
                    null
            );

            RiverSegment existing = segmentsById.get(id);
            if (existing == null || stitched.maxAccumulation() > existing.maxAccumulation()) {
                segmentsById.put(id, stitched);
            }
        }

        private RiverNetwork build() {
            RiverNetwork.Builder builder = RiverNetwork.builder();

            for (RiverNode node : nonMergedNodes.values()) {
                builder.addNode(node.withConnections(List.of(), List.of()));
            }

            for (NodeAccumulator accumulator : mergeNodes.values()) {
                builder.addNode(accumulator.toNode());
            }

            for (RiverSegment segment : segmentsById.values()) {
                builder.addSegment(segment);
            }

            return builder.build();
        }
    }

    private record NodeMergeKey(long qx, long qz) {
        private static NodeMergeKey from(RiverNode node) {
            long qx = Math.round(node.worldX() / BORDER_MATCH_GRID);
            long qz = Math.round(node.worldZ() / BORDER_MATCH_GRID);
            return new NodeMergeKey(qx, qz);
        }
    }

    private static final class NodeAccumulator {
        private final long canonicalId;
        private double sumX;
        private double sumZ;
        private int sumY;
        private float maxAccumulation;
        private float maxDischarge;
        private float maxWidth;
        private int count;
        private RiverNode.NodeType type = RiverNode.NodeType.BOUNDARY;

        private NodeAccumulator(long seed, NodeMergeKey key) {
            double x = key.qx() * BORDER_MATCH_GRID;
            double z = key.qz() * BORDER_MATCH_GRID;
            this.canonicalId = RiverNetwork.deterministicNodeId(seed, x, z, RiverNode.NodeType.BOUNDARY);
        }

        private long canonicalId() {
            return canonicalId;
        }

        private void add(RiverNode node) {
            sumX += node.worldX();
            sumZ += node.worldZ();
            sumY += node.surfaceY();
            maxAccumulation = Math.max(maxAccumulation, node.accumulation());
            maxDischarge = Math.max(maxDischarge, node.discharge());
            maxWidth = Math.max(maxWidth, node.widthBlocks());
            type = mergeType(type, node.type());
            count++;
        }

        private RiverNode toNode() {
            int safeCount = Math.max(1, count);
            return new RiverNode(
                    canonicalId,
                    sumX / safeCount,
                    sumZ / safeCount,
                    Math.round((float) sumY / safeCount),
                    type,
                    maxAccumulation,
                    maxDischarge,
                    maxWidth,
                    List.of(),
                    List.of()
            );
        }

        private static RiverNode.NodeType mergeType(RiverNode.NodeType a, RiverNode.NodeType b) {
            if (a == RiverNode.NodeType.CONFLUENCE || b == RiverNode.NodeType.CONFLUENCE) {
                return RiverNode.NodeType.CONFLUENCE;
            }
            if (a == RiverNode.NodeType.OUTLET || b == RiverNode.NodeType.OUTLET) {
                return RiverNode.NodeType.OUTLET;
            }
            if (a == RiverNode.NodeType.SOURCE || b == RiverNode.NodeType.SOURCE) {
                return RiverNode.NodeType.SOURCE;
            }
            if (a == RiverNode.NodeType.INTERNAL || b == RiverNode.NodeType.INTERNAL) {
                return RiverNode.NodeType.INTERNAL;
            }
            return RiverNode.NodeType.BOUNDARY;
        }
    }
}
