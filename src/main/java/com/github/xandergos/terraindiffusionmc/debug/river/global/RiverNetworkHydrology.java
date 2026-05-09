package com.github.xandergos.terraindiffusionmc.debug.river.global;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Recalculates global discharge width and depth after inter-region stitching.
 */
public final class RiverNetworkHydrology {
    private static final float LOCAL_SEGMENT_WEIGHT = 0.25F;
    private static final float MIN_WIDTH_BLOCKS = 2.0F;
    private static final float MAX_WIDTH_BLOCKS = 32.0F;
    private static final float MIN_DEPTH_BLOCKS = 0.35F;
    private static final float MAX_DEPTH_BLOCKS = 5.5F;

    private RiverNetworkHydrology() {
    }

    public static RiverNetwork recalculate(RiverNetwork network) {
        Map<Long, Float> nodeInflow = new HashMap<>();
        Map<Long, Float> segmentDischarge = new HashMap<>();
        Map<Long, Integer> remainingIncoming = new HashMap<>();
        Queue<Long> ready = new ArrayDeque<>();

        for (RiverNode node : network.nodes()) {
            int incoming = node.incomingSegmentIds().size();
            remainingIncoming.put(node.id(), incoming);
            nodeInflow.put(node.id(), 0.0F);
            if (incoming == 0) {
                ready.add(node.id());
            }
        }

        Set<Long> processedNodes = new HashSet<>();
        while (!ready.isEmpty()) {
            long nodeId = ready.remove();
            if (!processedNodes.add(nodeId)) {
                continue;
            }
            propagateFromNode(network, nodeId, nodeInflow, segmentDischarge, remainingIncoming, ready);
        }

        // Flat or cyclic leftovers should be rare. Process them once in descending Y order
        // instead of letting the graph disappear from the debug view.
        List<RiverNode> leftovers = new ArrayList<>();
        for (RiverNode node : network.nodes()) {
            if (!processedNodes.contains(node.id())) {
                leftovers.add(node);
            }
        }
        leftovers.sort((a, b) -> Integer.compare(b.surfaceY(), a.surfaceY()));
        for (RiverNode node : leftovers) {
            propagateFromNode(network, node.id(), nodeInflow, segmentDischarge, remainingIncoming, ready);
        }

        RiverNetwork.Builder builder = RiverNetwork.builder();

        for (RiverNode node : network.nodes()) {
            float q = Math.max(node.discharge(), nodeInflow.getOrDefault(node.id(), 0.0F));
            builder.addNode(new RiverNode(
                    node.id(),
                    node.worldX(),
                    node.worldZ(),
                    node.surfaceY(),
                    node.type(),
                    node.accumulation(),
                    q,
                    Math.max(node.widthBlocks(), widthFromDischarge(q)),
                    List.of(),
                    List.of()
            ));
        }

        for (RiverSegment segment : network.segments()) {
            float q = Math.max(segment.discharge(), segmentDischarge.getOrDefault(segment.id(), segment.maxAccumulation()));
            float maxWidth = Math.max(segment.maxWidthBlocks(), widthFromDischarge(q));
            float meanWidth = Math.max(segment.meanWidthBlocks(), maxWidth * 0.72F);
            float depth = Math.max(segment.depthBlocks(), depthFromDischarge(q));
            List<RiverPoint> points = rescalePoints(segment.polyline(), q, maxWidth, depth);

            builder.addSegment(new RiverSegment(
                    segment.id(),
                    segment.startNodeId(),
                    segment.endNodeId(),
                    points,
                    segment.meanAccumulation(),
                    Math.max(segment.maxAccumulation(), q),
                    q,
                    meanWidth,
                    maxWidth,
                    depth,
                    segment.downstreamDirection(),
                    null
            ));
        }

        return builder.build();
    }

    private static void propagateFromNode(
            RiverNetwork network,
            long nodeId,
            Map<Long, Float> nodeInflow,
            Map<Long, Float> segmentDischarge,
            Map<Long, Integer> remainingIncoming,
            Queue<Long> ready
    ) {
        RiverNode node = network.node(nodeId);
        if (node == null) {
            return;
        }

        float inflow = nodeInflow.getOrDefault(nodeId, 0.0F);
        float localNodeContribution = Math.max(node.accumulation(), node.discharge()) * 0.10F;
        int outgoingCount = Math.max(1, node.outgoingSegmentIds().size());

        for (long segmentId : node.outgoingSegmentIds()) {
            RiverSegment segment = network.segment(segmentId);
            if (segment == null) {
                continue;
            }

            float q = Math.max(
                    segment.maxAccumulation(),
                    segment.discharge()
            );
            q = Math.max(q, inflow / outgoingCount + localNodeContribution + segment.meanAccumulation() * LOCAL_SEGMENT_WEIGHT);
            segmentDischarge.put(segment.id(), q);

            long endNodeId = segment.endNodeId();
            nodeInflow.merge(endNodeId, q, Float::sum);
            remainingIncoming.computeIfPresent(endNodeId, (ignored, value) -> Math.max(0, value - 1));
            if (remainingIncoming.getOrDefault(endNodeId, 0) == 0) {
                ready.add(endNodeId);
            }
        }
    }

    private static List<RiverPoint> rescalePoints(List<RiverPoint> points, float segmentDischarge, float maxWidth, float depth) {
        List<RiverPoint> result = new ArrayList<>(points.size());
        float maxPointAccumulation = 0.0F;
        for (RiverPoint point : points) {
            maxPointAccumulation = Math.max(maxPointAccumulation, point.accumulation());
        }

        for (RiverPoint point : points) {
            float localT = maxPointAccumulation <= 0.0F ? 1.0F : clamp01(point.accumulation() / maxPointAccumulation);
            float q = Math.max(point.discharge(), segmentDischarge * (0.55F + 0.45F * localT));
            float width = Math.max(point.widthBlocks(), maxWidth * (0.60F + 0.40F * localT));
            result.add(new RiverPoint(
                    point.worldX(),
                    point.worldZ(),
                    point.surfaceY(),
                    Math.max(point.accumulation(), q),
                    q,
                    width,
                    depth,
                    point.downstreamDirection()
            ));
        }

        return result;
    }

    public static float widthFromDischarge(float discharge) {
        float value = (float) Math.sqrt(Math.log1p(Math.max(0.0F, discharge)));
        return clamp(MIN_WIDTH_BLOCKS + value * 2.75F, MIN_WIDTH_BLOCKS, MAX_WIDTH_BLOCKS);
    }

    public static float depthFromDischarge(float discharge) {
        float value = (float) Math.sqrt(Math.log1p(Math.max(0.0F, discharge)));
        return clamp(MIN_DEPTH_BLOCKS + value * 0.42F, MIN_DEPTH_BLOCKS, MAX_DEPTH_BLOCKS);
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
