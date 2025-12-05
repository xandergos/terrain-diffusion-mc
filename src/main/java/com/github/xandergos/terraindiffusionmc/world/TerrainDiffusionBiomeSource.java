package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.api.HeightmapApiClient;
import com.github.xandergos.terraindiffusionmc.api.HeightmapApiClient.HeightmapData;
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
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Map.entry;

public class TerrainDiffusionBiomeSource extends BiomeSource {
    private static final int TILE_SHIFT = 8; // 256 blocks
    private static final int TILE_SIZE = 1 << TILE_SHIFT;

    private static final RegistryKey<Biome> FOREST_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "forest_sparse"));
    private static final RegistryKey<Biome> TAIGA_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "taiga_sparse"));
    private static final RegistryKey<Biome> SNOWY_TAIGA_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "snowy_taiga_sparse"));

    public static final MapCodec<TerrainDiffusionBiomeSource> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Codec.STRING.fieldOf("api_url").orElse("http://localhost:8000").forGetter((TerrainDiffusionBiomeSource source) -> source.apiUrl),
                    RegistryOps.getEntryLookupCodec(RegistryKeys.BIOME)
            ).apply(instance, instance.stable(TerrainDiffusionBiomeSource::new)));


    private RegistryEntryLookup<Biome> biomeLookup;
    private Map<Short, RegistryEntry<Biome>> biomeIdMap = null;
    private final String apiUrl;
    private final HeightmapApiClient apiClient;

    public TerrainDiffusionBiomeSource(String apiUrl, RegistryEntryLookup<Biome> biomeLookup) {
        this.apiUrl = apiUrl;
        this.apiClient = new HeightmapApiClient(apiUrl);
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
                    entry((short) 3, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_PLAINS)),
                    entry((short) 5, this.biomeLookup.getOrThrow(BiomeKeys.DESERT)),
                    entry((short) 6, this.biomeLookup.getOrThrow(BiomeKeys.SWAMP)),
                    entry((short) 8, this.biomeLookup.getOrThrow(BiomeKeys.FOREST)),
                    entry((short) 15, this.biomeLookup.getOrThrow(BiomeKeys.TAIGA)),
                    entry((short) 16, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_TAIGA)),
                    entry((short) 17, this.biomeLookup.getOrThrow(BiomeKeys.SAVANNA)),
                    entry((short) 19, this.biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_HILLS)),
                    entry((short) 23, this.biomeLookup.getOrThrow(BiomeKeys.JUNGLE)),
                    entry((short) 26, this.biomeLookup.getOrThrow(BiomeKeys.BADLANDS)),
                    entry((short) 29, this.biomeLookup.getOrThrow(BiomeKeys.MEADOW)),
                    entry((short) 31, this.biomeLookup.getOrThrow(BiomeKeys.GROVE)),
                    entry((short) 32, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_SLOPES)),
                    entry((short) 33, this.biomeLookup.getOrThrow(BiomeKeys.FROZEN_PEAKS)),
                    entry((short) 35, this.biomeLookup.getOrThrow(BiomeKeys.STONY_PEAKS)),
                    entry((short) 41, this.biomeLookup.getOrThrow(BiomeKeys.WARM_OCEAN)),
                    entry((short) 44, this.biomeLookup.getOrThrow(BiomeKeys.OCEAN)),
                    entry((short) 46, this.biomeLookup.getOrThrow(BiomeKeys.COLD_OCEAN)),
                    entry((short) 48, this.biomeLookup.getOrThrow(BiomeKeys.FROZEN_OCEAN)),
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

        int tileX = blockX >> TILE_SHIFT;
        int tileZ = blockZ >> TILE_SHIFT;

        int blockStartX = tileX << TILE_SHIFT;
        int blockStartZ = tileZ << TILE_SHIFT;
        int blockEndX = blockStartX + TILE_SIZE;
        int blockEndZ = blockStartZ + TILE_SIZE;

        try {
            HeightmapData data = apiClient.fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX).get();
            if (data != null && data.biomeIds != null) {
                int localX = blockX - blockStartX;
                int localZ = blockZ - blockStartZ;

                // Clamp to safe ranges
                if (localX < 0) localX = 0;
                if (localZ < 0) localZ = 0;
                if (localX >= data.width) localX = data.width - 1;
                if (localZ >= data.height) localZ = data.height - 1;

                short biomeId = data.biomeIds[localZ][localX];
                return biomeIdMap.get((short) biomeId);
            }
        } catch (InterruptedException | ExecutionException e) {
            // Log error or handle gracefully?
            // For now, return default
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

