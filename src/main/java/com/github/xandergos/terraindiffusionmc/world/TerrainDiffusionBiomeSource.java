package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Map.entry;

public class TerrainDiffusionBiomeSource extends BiomeSource {
    private static final RegistryKey<Biome> FOREST_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "forest_sparse"));
    private static final RegistryKey<Biome> TAIGA_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "taiga_sparse"));
    private static final RegistryKey<Biome> SNOWY_TAIGA_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "snowy_taiga_sparse"));

    public static final MapCodec<TerrainDiffusionBiomeSource> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    RegistryOps.getEntryLookupCodec(RegistryKeys.BIOME)
            ).apply(instance, instance.stable(TerrainDiffusionBiomeSource::new)));


    private RegistryEntryLookup<Biome> biomeLookup;
    private Map<Short, RegistryEntry<Biome>> biomeIdMap = null;

    public TerrainDiffusionBiomeSource(RegistryEntryLookup<Biome> biomeLookup) {
        this.biomeLookup = biomeLookup;
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    private void requireBiomeIdMap() {
        if (biomeIdMap == null) {
            biomeIdMap = Map.ofEntries(
                    entry((short) 1, this.biomeLookup.getOrThrow(BiomeKeys.PLAINS)),
                    entry((short) 2, this.biomeLookup.getOrThrow(BiomeKeys.SUNFLOWER_PLAINS)),
                    entry((short) 3, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_PLAINS)),
                    entry((short) 4, this.biomeLookup.getOrThrow(BiomeKeys.ICE_SPIKES)),
                    entry((short) 5, this.biomeLookup.getOrThrow(BiomeKeys.DESERT)),
                    entry((short) 6, this.biomeLookup.getOrThrow(BiomeKeys.SWAMP)),
                    entry((short) 7, this.biomeLookup.getOrThrow(BiomeKeys.MANGROVE_SWAMP)),
                    entry((short) 8, this.biomeLookup.getOrThrow(BiomeKeys.FOREST)),
                    entry((short) 9, this.biomeLookup.getOrThrow(BiomeKeys.FLOWER_FOREST)),
                    entry((short) 10, this.biomeLookup.getOrThrow(BiomeKeys.BIRCH_FOREST)),
                    entry((short) 11, this.biomeLookup.getOrThrow(BiomeKeys.DARK_FOREST)),
                    entry((short) 12, this.biomeLookup.getOrThrow(BiomeKeys.OLD_GROWTH_BIRCH_FOREST)),
                    entry((short) 13, this.biomeLookup.getOrThrow(BiomeKeys.OLD_GROWTH_PINE_TAIGA)),
                    entry((short) 14, this.biomeLookup.getOrThrow(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA)),
                    entry((short) 15, this.biomeLookup.getOrThrow(BiomeKeys.TAIGA)),
                    entry((short) 16, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_TAIGA)),
                    entry((short) 17, this.biomeLookup.getOrThrow(BiomeKeys.SAVANNA)),
                    entry((short) 18, this.biomeLookup.getOrThrow(BiomeKeys.SAVANNA_PLATEAU)),
                    entry((short) 19, this.biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_HILLS)),
                    entry((short) 20, this.biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS)),
                    entry((short) 21, this.biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_FOREST)),
                    entry((short) 22, this.biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_SAVANNA)),
                    entry((short) 23, this.biomeLookup.getOrThrow(BiomeKeys.JUNGLE)),
                    entry((short) 24, this.biomeLookup.getOrThrow(BiomeKeys.SPARSE_JUNGLE)),
                    entry((short) 25, this.biomeLookup.getOrThrow(BiomeKeys.BAMBOO_JUNGLE)),
                    entry((short) 26, this.biomeLookup.getOrThrow(BiomeKeys.BADLANDS)),
                    entry((short) 27, this.biomeLookup.getOrThrow(BiomeKeys.ERODED_BADLANDS)),
                    entry((short) 28, this.biomeLookup.getOrThrow(BiomeKeys.WOODED_BADLANDS)),
                    entry((short) 29, this.biomeLookup.getOrThrow(BiomeKeys.MEADOW)),
                    entry((short) 30, this.biomeLookup.getOrThrow(BiomeKeys.CHERRY_GROVE)),
                    entry((short) 31, this.biomeLookup.getOrThrow(BiomeKeys.GROVE)),
                    entry((short) 32, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_SLOPES)),
                    entry((short) 33, this.biomeLookup.getOrThrow(BiomeKeys.FROZEN_PEAKS)),
                    entry((short) 34, this.biomeLookup.getOrThrow(BiomeKeys.JAGGED_PEAKS)),
                    entry((short) 35, this.biomeLookup.getOrThrow(BiomeKeys.STONY_PEAKS)),
                    entry((short) 36, this.biomeLookup.getOrThrow(BiomeKeys.RIVER)),
                    entry((short) 37, this.biomeLookup.getOrThrow(BiomeKeys.FROZEN_RIVER)),
                    entry((short) 38, this.biomeLookup.getOrThrow(BiomeKeys.BEACH)),
                    entry((short) 39, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_BEACH)),
                    entry((short) 40, this.biomeLookup.getOrThrow(BiomeKeys.STONY_SHORE)),
                    entry((short) 41, this.biomeLookup.getOrThrow(BiomeKeys.WARM_OCEAN)),
                    entry((short) 42, this.biomeLookup.getOrThrow(BiomeKeys.LUKEWARM_OCEAN)),
                    entry((short) 43, this.biomeLookup.getOrThrow(BiomeKeys.DEEP_LUKEWARM_OCEAN)),
                    entry((short) 44, this.biomeLookup.getOrThrow(BiomeKeys.OCEAN)),
                    entry((short) 45, this.biomeLookup.getOrThrow(BiomeKeys.DEEP_OCEAN)),
                    entry((short) 46, this.biomeLookup.getOrThrow(BiomeKeys.COLD_OCEAN)),
                    entry((short) 47, this.biomeLookup.getOrThrow(BiomeKeys.DEEP_COLD_OCEAN)),
                    entry((short) 48, this.biomeLookup.getOrThrow(BiomeKeys.FROZEN_OCEAN)),
                    entry((short) 49, this.biomeLookup.getOrThrow(BiomeKeys.DEEP_FROZEN_OCEAN)),
                    entry((short) 50, this.biomeLookup.getOrThrow(BiomeKeys.MUSHROOM_FIELDS)),
                    entry((short) 108, this.biomeLookup.getOrThrow(FOREST_SPARSE)),
                    entry((short) 115, this.biomeLookup.getOrThrow(TAIGA_SPARSE)),
                    entry((short) 116, this.biomeLookup.getOrThrow(SNOWY_TAIGA_SPARSE))
            );
        }
    }

    @Override
    public Stream<RegistryEntry<Biome>> biomeStream() {
        requireBiomeIdMap();
        return biomeIdMap.values().stream();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        requireBiomeIdMap();
        RegistryEntry<Biome> defaultEntry = biomeIdMap.get((short) 1);

        // x, y, z are in quart coordinates (block / 4)
        int blockX = BiomeCoords.toBlock(x);
        int blockZ = BiomeCoords.toBlock(z);

        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = blockX >> tileShift;
        int tileZ = blockZ >> tileShift;

        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;
        int blockEndX = blockStartX + tileSize;
        int blockEndZ = blockStartZ + tileSize;

        HeightmapData data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        if (data != null && data.biomeIds != null) {
            int localX = Math.max(0, Math.min(data.width  - 1, blockX - blockStartX));
            int localZ = Math.max(0, Math.min(data.height - 1, blockZ - blockStartZ));
            RegistryEntry<Biome> entry = biomeIdMap.get(data.biomeIds[localZ][localX]);
            if (entry != null) return entry;
        }

        return defaultEntry;
    }

    @Override
    public Pair<BlockPos, RegistryEntry<Biome>> locateBiome(BlockPos origin, int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval, Predicate<RegistryEntry<Biome>> predicate, MultiNoiseUtil.MultiNoiseSampler noiseSampler, WorldView world) {
        return null;
    }

    @Override
    public Pair<BlockPos, RegistryEntry<Biome>> locateBiome(int x, int y, int z, int radius, int blockCheckInterval, Predicate<RegistryEntry<Biome>> predicate, Random random, boolean bl, MultiNoiseUtil.MultiNoiseSampler noiseSampler) {
        return null;
    }
}

