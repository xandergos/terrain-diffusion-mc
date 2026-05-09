package com.github.xandergos.terraindiffusionmc.debug.river.global;

import java.util.List;

/**
 * River graph generated for one large world-space region.
 */
public record RiverRegionNetwork(
        RiverRegionKey key,
        int haloBlocks,
        RiverNetwork network
) {
    public RiverRegionNetwork {
        if (key == null) {
            throw new IllegalArgumentException("RiverRegionNetwork key cannot be null");
        }
        if (network == null) {
            throw new IllegalArgumentException("RiverRegionNetwork network cannot be null");
        }
        haloBlocks = Math.max(0, haloBlocks);
    }

    public int interiorMinX() {
        return key.blockStartX();
    }

    public int interiorMinZ() {
        return key.blockStartZ();
    }

    public int interiorMaxXExclusive() {
        return key.blockEndXExclusive();
    }

    public int interiorMaxZExclusive() {
        return key.blockEndZExclusive();
    }

    public int expandedMinX() {
        return interiorMinX() - haloBlocks;
    }

    public int expandedMinZ() {
        return interiorMinZ() - haloBlocks;
    }

    public int expandedMaxXExclusive() {
        return interiorMaxXExclusive() + haloBlocks;
    }

    public int expandedMaxZExclusive() {
        return interiorMaxZExclusive() + haloBlocks;
    }

    public boolean isInsideInterior(double worldX, double worldZ) {
        return worldX >= interiorMinX()
                && worldZ >= interiorMinZ()
                && worldX < interiorMaxXExclusive()
                && worldZ < interiorMaxZExclusive();
    }

    public List<RiverSegment> queryInteriorAabb(double minX, double minZ, double maxX, double maxZ) {
        return network.queryAabb(minX, minZ, maxX, maxZ).stream()
                .filter(segment -> segment.bounds().intersects(interiorMinX(), interiorMinZ(), interiorMaxXExclusive(), interiorMaxZExclusive()))
                .toList();
    }
}
