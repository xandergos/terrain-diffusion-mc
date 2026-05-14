package com.github.xandergos.terraindiffusionmc.debug;

import com.github.xandergos.terraindiffusionmc.world.HeightConverter;

/**
 * Immutable debug clone of a generated terrain tile.
 *
 * <p>Coordinates are stored in Minecraft block space. The flat arrays use
 * row-major order with localZ as the row and localX as the column :</p>
 *
 * <pre>
 * index = localZ * width + localX
 * </pre>
 */
public record TerrainBaseTile(
        int blockStartX,
        int blockStartZ,
        int width,
        int height,
        int configuredScale,
        short[] baseMeters,
        short[] generatedMeters,
        short[] biomeIds,
        int[] baseY,
        int[] generatedY
) {
    public TerrainBaseTile {
        int expectedLength = width * height;
        if (baseMeters.length != expectedLength
                || generatedMeters.length != expectedLength
                || biomeIds.length != expectedLength
                || baseY.length != expectedLength
                || generatedY.length != expectedLength) {
            throw new IllegalArgumentException("All tile arrays must have width * height elements");
        }
    }

    public int index(int localX, int localZ) {
        return localZ * width + localX;
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return blockX >= blockStartX
                && blockZ >= blockStartZ
                && blockX < blockStartX + width
                && blockZ < blockStartZ + height;
    }

    public int localX(int blockX) {
        return blockX - blockStartX;
    }

    public int localZ(int blockZ) {
        return blockZ - blockStartZ;
    }

    public short baseMetersAtLocal(int localX, int localZ) {
        return baseMeters[index(localX, localZ)];
    }

    public short generatedMetersAtLocal(int localX, int localZ) {
        return generatedMeters[index(localX, localZ)];
    }

    public int baseYAtLocal(int localX, int localZ) {
        return baseY[index(localX, localZ)];
    }

    public int generatedYAtLocal(int localX, int localZ) {
        return generatedY[index(localX, localZ)];
    }

    public short biomeAtLocal(int localX, int localZ) {
        return biomeIds[index(localX, localZ)];
    }

    public int deltaYAtLocal(int localX, int localZ) {
        int idx = index(localX, localZ);
        return generatedY[idx] - baseY[idx];
    }

    public static TerrainBaseTile fromFlatArrays(
            int blockStartX,
            int blockStartZ,
            int width,
            int height,
            int configuredScale,
            float[] baseMetersFloat,
            float[] generatedMetersFloat,
            short[] biomeIds
    ) {
        int len = width * height;
        if (baseMetersFloat.length != len || generatedMetersFloat.length != len || biomeIds.length != len) {
            throw new IllegalArgumentException("Input arrays must have width * height elements");
        }

        short[] baseMeters = new short[len];
        short[] generatedMeters = new short[len];
        short[] biomeIdsCopy = biomeIds.clone();
        int[] baseY = new int[len];
        int[] generatedY = new int[len];

        for (int idx = 0; idx < len; idx++) {
            short base = clampToShort(Math.round(baseMetersFloat[idx]));
            short generated = clampToShort(Math.round(generatedMetersFloat[idx]));

            baseMeters[idx] = base;
            generatedMeters[idx] = generated;
            baseY[idx] = HeightConverter.convertToMinecraftHeight(base, configuredScale);
            generatedY[idx] = HeightConverter.convertToMinecraftHeight(generated, configuredScale);
        }

        return new TerrainBaseTile(
                blockStartX,
                blockStartZ,
                width,
                height,
                configuredScale,
                baseMeters,
                generatedMeters,
                biomeIdsCopy,
                baseY,
                generatedY
        );
    }

    private static short clampToShort(int value) {
        if (value < Short.MIN_VALUE) return Short.MIN_VALUE;
        if (value > Short.MAX_VALUE) return Short.MAX_VALUE;
        return (short) value;
    }
}
