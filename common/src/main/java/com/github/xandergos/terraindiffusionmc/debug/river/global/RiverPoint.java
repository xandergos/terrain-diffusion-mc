package com.github.xandergos.terraindiffusionmc.debug.river.global;

/**
 * Immutable point along a global river segment polyline.
 *
 * <p>Coordinates are in Minecraft world block coordinates. The Y value is the
 * terrain surface Y used by the river generator at this point, not necessarily
 * the final carved bed Y.</p>
 */
public record RiverPoint(
        double worldX,
        double worldZ,
        int surfaceY,
        float accumulation,
        float discharge,
        float widthBlocks,
        float depthBlocks,
        byte downstreamDirection
) {
    public RiverPoint {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) {
            throw new IllegalArgumentException("RiverPoint coordinates must be finite");
        }
        accumulation = Math.max(0.0F, accumulation);
        discharge = Math.max(0.0F, discharge);
        widthBlocks = Math.max(0.0F, widthBlocks);
        depthBlocks = Math.max(0.0F, depthBlocks);
    }
}
