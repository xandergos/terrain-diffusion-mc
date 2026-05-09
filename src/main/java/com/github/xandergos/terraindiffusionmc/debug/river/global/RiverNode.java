package com.github.xandergos.terraindiffusionmc.debug.river.global;

import java.util.List;

/**
 * Stable graph node in the global river network.
 */
public record RiverNode(
        long id,
        double worldX,
        double worldZ,
        int surfaceY,
        NodeType type,
        float accumulation,
        float discharge,
        float widthBlocks,
        List<Long> incomingSegmentIds,
        List<Long> outgoingSegmentIds
) {
    public enum NodeType {
        SOURCE,
        CONFLUENCE,
        OUTLET,
        LAKE_INLET,
        LAKE_OUTLET,
        OCEAN_OUTLET,
        BOUNDARY,
        INTERNAL
    }

    public RiverNode {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) {
            throw new IllegalArgumentException("RiverNode coordinates must be finite");
        }
        if (type == null) {
            throw new IllegalArgumentException("RiverNode type cannot be null");
        }
        accumulation = Math.max(0.0F, accumulation);
        discharge = Math.max(0.0F, discharge);
        widthBlocks = Math.max(0.0F, widthBlocks);
        incomingSegmentIds = List.copyOf(incomingSegmentIds);
        outgoingSegmentIds = List.copyOf(outgoingSegmentIds);
    }

    public RiverNode withConnections(List<Long> incomingSegmentIds, List<Long> outgoingSegmentIds) {
        return new RiverNode(
                id,
                worldX,
                worldZ,
                surfaceY,
                type,
                accumulation,
                discharge,
                widthBlocks,
                incomingSegmentIds,
                outgoingSegmentIds
        );
    }
}
