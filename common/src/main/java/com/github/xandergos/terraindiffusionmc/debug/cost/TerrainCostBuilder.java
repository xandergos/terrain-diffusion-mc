package com.github.xandergos.terraindiffusionmc.debug.cost;

import com.github.xandergos.terraindiffusionmc.debug.TerrainBaseTile;

public final class TerrainCostBuilder {
    private TerrainCostBuilder() {
    }

    public static TerrainCostTile build(TerrainBaseTile base) {
        return buildCropped(base, base.blockStartX(), base.blockStartZ(), base.width(), base.height());
    }

    public static TerrainCostTile buildCropped(TerrainBaseTile source, int blockStartX, int blockStartZ, int width, int height) {
        int len = width * height;

        int[] surfaceY = new int[len];
        float[] total = new float[len];
        float[] slope = new float[len];
        float[] ridge = new float[len];
        float[] valley = new float[len];
        float[] biome = new float[len];
        float[] soil = new float[len];
        float[] forbidden = new float[len];

        int sourceOffsetX = blockStartX - source.blockStartX();
        int sourceOffsetZ = blockStartZ - source.blockStartZ();

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = z * width + x;
                int sx = sourceOffsetX + x;
                int sz = sourceOffsetZ + z;

                float center = baseMeters(source, sx, sz);
                float west = baseMeters(source, sx - 1, sz);
                float east = baseMeters(source, sx + 1, sz);
                float north = baseMeters(source, sx, sz - 1);
                float south = baseMeters(source, sx, sz + 1);
                float northWest = baseMeters(source, sx - 1, sz - 1);
                float northEast = baseMeters(source, sx + 1, sz - 1);
                float southWest = baseMeters(source, sx - 1, sz + 1);
                float southEast = baseMeters(source, sx + 1, sz + 1);

                float dx = (east - west) * 0.5F;
                float dz = (south - north) * 0.5F;
                float slopeMagnitude = (float) Math.sqrt(dx * dx + dz * dz);

                float neighborhoodAverage = (west + east + north + south + northWest + northEast + southWest + southEast) * 0.125F;
                float topographicPosition = center - neighborhoodAverage;

                float ridgeSignal = Math.max(0.0F, topographicPosition);
                float valleySignal = Math.max(0.0F, -topographicPosition);

                float slopeCost = smoothstep(
                        TerrainCostConfig.SLOPE_START,
                        TerrainCostConfig.SLOPE_END,
                        slopeMagnitude
                ) * TerrainCostConfig.SLOPE_WEIGHT;

                float ridgeCost = smoothstep(
                        TerrainCostConfig.RIDGE_START,
                        TerrainCostConfig.RIDGE_END,
                        ridgeSignal
                ) * TerrainCostConfig.RIDGE_WEIGHT;

                float valleyBonus = smoothstep(
                        TerrainCostConfig.VALLEY_START,
                        TerrainCostConfig.VALLEY_END,
                        valleySignal
                ) * TerrainCostConfig.VALLEY_WEIGHT;

                short biomeId = biome(source, sx, sz);
                float biomeCost = biomeCost(biomeId);
                float soilCost = soilCost(biomeId, slopeMagnitude);
                float forbiddenCost = forbiddenCost(biomeId);

                float rawTotal = TerrainCostConfig.BASE_COST
                        + slopeCost
                        + ridgeCost
                        + biomeCost
                        + soilCost
                        + forbiddenCost
                        - valleyBonus;

                surfaceY[idx] = generatedY(source, sx, sz);
                slope[idx] = slopeCost;
                ridge[idx] = ridgeCost;
                valley[idx] = valleyBonus;
                biome[idx] = biomeCost;
                soil[idx] = soilCost;
                forbidden[idx] = forbiddenCost;
                total[idx] = clamp01(rawTotal);
            }
        }

        return new TerrainCostTile(
                blockStartX,
                blockStartZ,
                width,
                height,
                surfaceY,
                total,
                slope,
                ridge,
                valley,
                biome,
                soil,
                forbidden
        );
    }

    private static float baseMeters(TerrainBaseTile tile, int localX, int localZ) {
        return tile.baseMetersAtLocal(
                clamp(localX, 0, tile.width() - 1),
                clamp(localZ, 0, tile.height() - 1)
        );
    }

    private static int generatedY(TerrainBaseTile tile, int localX, int localZ) {
        return tile.generatedYAtLocal(
                clamp(localX, 0, tile.width() - 1),
                clamp(localZ, 0, tile.height() - 1)
        );
    }

    private static short biome(TerrainBaseTile tile, int localX, int localZ) {
        return tile.biomeAtLocal(
                clamp(localX, 0, tile.width() - 1),
                clamp(localZ, 0, tile.height() - 1)
        );
    }

    private static float biomeCost(short biomeId) {
        return switch (biomeId) {
            case 6, 41, 44, 46, 48 -> 0.00F;
            case 1, 8, 15, 17, 23, 29, 108, 115 -> 0.06F;
            case 3, 16, 31, 116 -> 0.10F;
            case 5 -> 0.18F;
            case 19, 26, 32, 33, 35 -> 0.32F;
            default -> TerrainCostConfig.DEFAULT_BIOME_COST;
        };
    }

    private static float soilCost(short biomeId, float slopeMagnitude) {
        float base = switch (biomeId) {
            case 6 -> 0.00F;
            case 1, 3, 8, 15, 17, 23, 29, 108, 115, 116 -> 0.03F;
            case 5 -> 0.05F;
            case 19, 26, 32, 33, 35 -> 0.22F;
            default -> 0.05F;
        };

        if (slopeMagnitude > 0.85F) {
            base += 0.08F;
        }

        return clamp01(base);
    }

    private static float forbiddenCost(short biomeId) {
        return 0.0F;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0.0F : 1.0F;
        }

        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
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
}
