package com.github.xandergos.terraindiffusionmc.debug.river.overlay;

import com.github.xandergos.terraindiffusionmc.debug.TerrainBaseTile;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverNetwork;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverSpatialIndex;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverVectorConfig;

import java.util.List;

public final class TerrainRiverChunkOverlayBuilder {
    private TerrainRiverChunkOverlayBuilder() {
    }

    public static TerrainRiverChunkOverlay build(TerrainBaseTile terrain, TerrainRiverSpatialIndex spatialIndex) {
        int width = terrain.width();
        int height = terrain.height();
        int len = width * height;

        int[] surfaceY = new int[len];
        float[] distance = new float[len];
        float[] bed = new float[len];
        float[] water = new float[len];
        float[] bank = new float[len];
        float[] material = new float[len];
        float[] vegetation = new float[len];
        float[] correction = new float[len];

        int padding = TerrainRiverChunkOverlayConfig.SEGMENT_QUERY_PADDING_BLOCKS;
        List<TerrainRiverNetwork.Segment> nearbySegments = spatialIndex.queryAabb(
                terrain.blockStartX() - padding,
                terrain.blockStartZ() - padding,
                terrain.blockStartX() + width + padding,
                terrain.blockStartZ() + height + padding
        );

        float maxCorrection = 0.0F;

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = z * width + x;
                surfaceY[idx] = terrain.generatedYAtLocal(x, z);

                double worldX = terrain.blockStartX() + x + 0.5D;
                double worldZ = terrain.blockStartZ() + z + 0.5D;
                Nearest nearest = nearestSegmentPoint(worldX, worldZ, nearbySegments);

                if (!nearest.valid()) {
                    distance[idx] = 1.0F;
                    continue;
                }

                float channelRadius = Math.max(
                        TerrainRiverChunkOverlayConfig.MIN_CHANNEL_RADIUS_BLOCKS,
                        nearest.widthBlocks() * 0.5F
                );
                float bankRadius = channelRadius + TerrainRiverChunkOverlayConfig.BANK_EXTRA_BLOCKS;
                float wetRadius = bankRadius + TerrainRiverChunkOverlayConfig.WET_EXTRA_BLOCKS;
                float vegetationRadius = wetRadius + TerrainRiverChunkOverlayConfig.VEGETATION_EXTRA_BLOCKS;

                float d = nearest.distanceBlocks();
                float normalizedDistance = clamp01(d / vegetationRadius);
                float bedStrength = 1.0F - smoothstep(channelRadius * 0.25F, channelRadius, d);
                float waterStrength = 1.0F - smoothstep(channelRadius * 0.15F, channelRadius * 0.72F, d);
                float bankStrength = ring(d, channelRadius * 0.85F, bankRadius);
                float materialStrength = 1.0F - smoothstep(channelRadius, wetRadius, d);
                float vegetationStrength = ring(d, wetRadius * 0.72F, vegetationRadius);

                float cutDepth = Math.min(
                        TerrainRiverChunkOverlayConfig.MAX_TERRAIN_CORRECTION_BLOCKS,
                        nearest.depthBlocks() * (0.65F + bedStrength * 0.65F)
                );
                float terrainCorrection = cutDepth * bedStrength
                        + TerrainRiverChunkOverlayConfig.BANK_RAISE_PREVIEW_BLOCKS * bankStrength;

                distance[idx] = normalizedDistance;
                bed[idx] = clamp01(bedStrength);
                water[idx] = clamp01(waterStrength);
                bank[idx] = clamp01(bankStrength);
                material[idx] = clamp01(materialStrength);
                vegetation[idx] = clamp01(vegetationStrength);
                correction[idx] = Math.max(0.0F, terrainCorrection);
                maxCorrection = Math.max(maxCorrection, correction[idx]);
            }
        }

        return new TerrainRiverChunkOverlay(
                terrain.blockStartX(),
                terrain.blockStartZ(),
                width,
                height,
                surfaceY,
                distance,
                bed,
                water,
                bank,
                material,
                vegetation,
                correction,
                maxCorrection
        );
    }

    private static Nearest nearestSegmentPoint(double worldX, double worldZ, List<TerrainRiverNetwork.Segment> segments) {
        Nearest best = Nearest.invalid();

        for (TerrainRiverNetwork.Segment segment : segments) {
            List<TerrainRiverNetwork.Point> points = segment.points();
            for (int i = 0; i < points.size() - 1; i++) {
                TerrainRiverNetwork.Point a = points.get(i);
                TerrainRiverNetwork.Point b = points.get(i + 1);
                Nearest candidate = nearestOnLine(worldX, worldZ, a, b, segment.depthBlocks());
                if (candidate.distanceBlocks() < best.distanceBlocks()) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private static Nearest nearestOnLine(
            double worldX,
            double worldZ,
            TerrainRiverNetwork.Point a,
            TerrainRiverNetwork.Point b,
            float segmentDepth
    ) {
        double ax = a.worldX();
        double az = a.worldZ();
        double bx = b.worldX();
        double bz = b.worldZ();
        double vx = bx - ax;
        double vz = bz - az;
        double lengthSq = vx * vx + vz * vz;

        double t = lengthSq <= 1.0E-6D
                ? 0.0D
                : clamp01(((worldX - ax) * vx + (worldZ - az) * vz) / lengthSq);

        double px = ax + vx * t;
        double pz = az + vz * t;
        double dx = worldX - px;
        double dz = worldZ - pz;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float width = lerp(a.widthBlocks(), b.widthBlocks(), (float) t);
        float depth = Math.max(TerrainRiverVectorConfig.MIN_DEPTH_BLOCKS, segmentDepth);

        return new Nearest(true, distance, width, depth);
    }

    private static float ring(float x, float inner, float outer) {
        if (outer <= inner) {
            return 0.0F;
        }
        float enter = smoothstep(inner, (inner + outer) * 0.5F, x);
        float exit = 1.0F - smoothstep((inner + outer) * 0.5F, outer, x);
        return clamp01(enter * exit);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0.0F : 1.0F;
        }
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    private static float clamp01(double value) {
        if (value < 0.0D) {
            return 0.0F;
        }
        if (value > 1.0D) {
            return 1.0F;
        }
        return (float) value;
    }

    private record Nearest(boolean valid, float distanceBlocks, float widthBlocks, float depthBlocks) {
        private static Nearest invalid() {
            return new Nearest(false, Float.POSITIVE_INFINITY, 0.0F, 0.0F);
        }
    }
}
