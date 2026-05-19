package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.biome.BiomePalette;
import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * Column-aware fluid picker for Terrain Diffusion worlds.
 *
 * <p>The vanilla disabled aquifer path gives the clean sea-level ocean fill that reaches the
 * shore, but its default fluid picker floods every generated air pocket below sea level. The
 * vanilla enabled aquifer path avoids that cave flooding, but in Terrain Diffusion's custom
 * 2D-heightmap terrain it can pull ocean water away from the coastline. This picker keeps the
 * deterministic sea-level fill only for actual aquatic/coastal columns and leaves inland caves dry.</p>
 */
public final class TerrainDiffusionFluidPicker implements Aquifer.FluidPicker {
    public static final TerrainDiffusionFluidPicker INSTANCE = new TerrainDiffusionFluidPicker();

    public static final int SEA_LEVEL = 63;

    private static final Aquifer.FluidStatus WATER = new Aquifer.FluidStatus(SEA_LEVEL, Blocks.WATER.defaultBlockState());
    private static final Aquifer.FluidStatus DRY = new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState());

    /**
     * Allow a little water below the predicted sea floor so shallow cave mouths / gouged ocean
     * bottoms do not leave dry air cracks at the coast. Deeper caves stay dry.
     */
    private static final int OCEAN_SEAFLOOR_SEEP_DEPTH = 5;
    private static final int RIVER_SEAFLOOR_SEEP_DEPTH = 2;
    private static final int SWAMP_SEAFLOOR_SEEP_DEPTH = 1;
    private static final int COASTAL_LAND_SEEP_DEPTH = 2;

    /**
     * Conservative fallback used only if Terrain Diffusion data is not available yet for the
     * sampled column. It preserves shallow shoreline water without turning deep caves into lakes.
     */
    private static final int UNKNOWN_COLUMN_MIN_WATER_Y = SEA_LEVEL - 16;

    private TerrainDiffusionFluidPicker() {}

    @Override
    public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
        Column column = sampleColumn(x, z);
        if (column == null) {
            return y >= UNKNOWN_COLUMN_MIN_WATER_Y ? WATER : DRY;
        }

        if (isAquatic(column.biomeId)) {
            return isNearColumnSurface(y, column.surfaceY, seafloorSeepDepth(column.biomeId)) ? WATER : DRY;
        }

        if (isSwamp(column.biomeId)) {
            return isNearColumnSurface(y, column.surfaceY, SWAMP_SEAFLOOR_SEEP_DEPTH) ? WATER : DRY;
        }

        // Beach/coastal transition columns often classify as land before the terrain rises above
        // sea level. Keep only the shallow part of those low columns wet so oceans and rivers
        // physically touch the shore without flooding unrelated caves underneath.
        if (column.surfaceY < SEA_LEVEL
                && isNearColumnSurface(y, column.surfaceY, COASTAL_LAND_SEEP_DEPTH)
                && hasNearbyAquaticColumn(x, z, 24)) {
            return WATER;
        }

        return DRY;
    }

    private static boolean isNearColumnSurface(int y, int surfaceY, int seepDepth) {
        return y >= surfaceY - seepDepth;
    }

    private static int seafloorSeepDepth(short biomeId) {
        return switch (biomeId) {
            case BiomePalette.RIVER, BiomePalette.FROZEN_RIVER -> RIVER_SEAFLOOR_SEEP_DEPTH;
            default -> OCEAN_SEAFLOOR_SEEP_DEPTH;
        };
    }

    private static boolean hasNearbyAquaticColumn(int x, int z, int radius) {
        // Cross + diagonals, cheap enough for aquifer sampling and wide enough to bridge one
        // hachured biome-transition band without flooding whole inland basins.
        return isAquaticColumn(x + radius, z)
                || isAquaticColumn(x - radius, z)
                || isAquaticColumn(x, z + radius)
                || isAquaticColumn(x, z - radius)
                || isAquaticColumn(x + radius, z + radius)
                || isAquaticColumn(x + radius, z - radius)
                || isAquaticColumn(x - radius, z + radius)
                || isAquaticColumn(x - radius, z - radius);
    }

    private static boolean isAquaticColumn(int x, int z) {
        Column column = sampleColumn(x, z);
        return column != null && isAquatic(column.biomeId);
    }

    private static Column sampleColumn(int x, int z) {
        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = x >> tileShift;
        int tileZ = z >> tileShift;

        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;
        int blockEndX = blockStartX + tileSize;
        int blockEndZ = blockStartZ + tileSize;

        HeightmapData data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        if (data == null || data.heightmap == null) {
            return null;
        }

        int localX = Math.max(0, Math.min(data.width - 1, x - blockStartX));
        int localZ = Math.max(0, Math.min(data.height - 1, z - blockStartZ));

        int surfaceY = HeightConverter.convertToMinecraftHeight(data.heightmap[localZ][localX]);
        short biomeId = BiomePalette.DEFAULT;
        if (data.biomeIds != null) {
            biomeId = data.biomeIds[localZ][localX];
        }

        return new Column(surfaceY, biomeId);
    }

    private static boolean isAquatic(short biomeId) {
        return switch (biomeId) {
            case BiomePalette.RIVER,
                 BiomePalette.FROZEN_RIVER,
                 BiomePalette.WARM_OCEAN,
                 BiomePalette.LUKEWARM_OCEAN,
                 BiomePalette.DEEP_LUKEWARM_OCEAN,
                 BiomePalette.OCEAN,
                 BiomePalette.DEEP_OCEAN,
                 BiomePalette.COLD_OCEAN,
                 BiomePalette.DEEP_COLD_OCEAN,
                 BiomePalette.FROZEN_OCEAN,
                 BiomePalette.DEEP_FROZEN_OCEAN -> true;
            default -> false;
        };
    }

    private static boolean isSwamp(short biomeId) {
        return biomeId == BiomePalette.SWAMP || biomeId == BiomePalette.MANGROVE_SWAMP;
    }

    private record Column(int surfaceY, short biomeId) {}
}
