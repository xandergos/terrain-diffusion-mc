package com.github.xandergos.terraindiffusionmc.debug.river.global;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable directed graph for global river data.
 *
 * <p>This is intentionally independent from the existing local/debug raster and
 * vector pipeline. Region builders and stitching can populate this graph later
 * without changing chunk application code.</p>
 */
public final class RiverNetwork {
    private final Map<Long, RiverNode> nodesById;
    private final Map<Long, RiverSegment> segmentsById;

    public RiverNetwork(Map<Long, RiverNode> nodesById, Map<Long, RiverSegment> segmentsById) {
        this.nodesById = Collections.unmodifiableMap(new LinkedHashMap<>(nodesById));
        this.segmentsById = Collections.unmodifiableMap(new LinkedHashMap<>(segmentsById));
    }

    public Map<Long, RiverNode> nodesById() {
        return nodesById;
    }

    public Map<Long, RiverSegment> segmentsById() {
        return segmentsById;
    }

    public List<RiverNode> nodes() {
        return List.copyOf(nodesById.values());
    }

    public List<RiverSegment> segments() {
        return List.copyOf(segmentsById.values());
    }

    public RiverNode node(long id) {
        return nodesById.get(id);
    }

    public RiverSegment segment(long id) {
        return segmentsById.get(id);
    }

    public List<RiverSegment> queryAabb(double minX, double minZ, double maxX, double maxZ) {
        List<RiverSegment> result = new ArrayList<>();
        for (RiverSegment segment : segmentsById.values()) {
            if (segment.intersectsAabb(minX, minZ, maxX, maxZ)) {
                result.add(segment);
            }
        }
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static long deterministicNodeId(long seed, double worldX, double worldZ, RiverNode.NodeType type) {
        long qx = Math.round(worldX * 16.0D);
        long qz = Math.round(worldZ * 16.0D);
        long h = seed;
        h = mix64(h ^ qx);
        h = mix64(h ^ Long.rotateLeft(qz, 23));
        h = mix64(h ^ type.ordinal());
        return h;
    }

    public static long deterministicSegmentId(long seed, long startNodeId, long endNodeId, int pathSignature) {
        long h = seed;
        h = mix64(h ^ startNodeId);
        h = mix64(h ^ Long.rotateLeft(endNodeId, 17));
        h = mix64(h ^ pathSignature);
        return h;
    }

    public static int pathSignature(List<RiverPoint> points) {
        int h = 1;
        for (RiverPoint point : points) {
            int qx = (int) Math.round(point.worldX() * 8.0D);
            int qz = (int) Math.round(point.worldZ() * 8.0D);
            h = 31 * h + qx;
            h = 31 * h + qz;
        }
        return h;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static final class Builder {
        private final Map<Long, RiverNode> nodes = new LinkedHashMap<>();
        private final Map<Long, RiverSegment> segments = new LinkedHashMap<>();
        private final Map<Long, List<Long>> incomingByNode = new HashMap<>();
        private final Map<Long, List<Long>> outgoingByNode = new HashMap<>();

        public Builder addNode(RiverNode node) {
            nodes.put(node.id(), node);
            incomingByNode.computeIfAbsent(node.id(), ignored -> new ArrayList<>());
            outgoingByNode.computeIfAbsent(node.id(), ignored -> new ArrayList<>());
            return this;
        }

        public Builder addSegment(RiverSegment segment) {
            segments.put(segment.id(), segment);
            outgoingByNode.computeIfAbsent(segment.startNodeId(), ignored -> new ArrayList<>()).add(segment.id());
            incomingByNode.computeIfAbsent(segment.endNodeId(), ignored -> new ArrayList<>()).add(segment.id());
            return this;
        }

        public RiverNetwork build() {
            Map<Long, RiverNode> connectedNodes = new LinkedHashMap<>();
            for (RiverNode node : nodes.values()) {
                connectedNodes.put(node.id(), node.withConnections(
                        incomingByNode.getOrDefault(node.id(), List.of()),
                        outgoingByNode.getOrDefault(node.id(), List.of())
                ));
            }
            return new RiverNetwork(connectedNodes, segments);
        }
    }
}
