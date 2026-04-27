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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TerrainDiffusionBiomeSource extends BiomeSource {
    private static final RegistryKey<Biome> FOREST_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "forest_sparse"));
    private static final RegistryKey<Biome> TAIGA_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "taiga_sparse"));
    private static final RegistryKey<Biome> SNOWY_TAIGA_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "snowy_taiga_sparse"));

    // Oh The Biomes We've Gone biome keys
    private static RegistryKey<Biome> bwg(String name) {
        return RegistryKey.of(RegistryKeys.BIOME, Identifier.of("biomeswevegone", name));
    }
    private static final RegistryKey<Biome> BWG_ALLIUM_SHRUBLAND         = bwg("allium_shrubland");
    private static final RegistryKey<Biome> BWG_AMARANTH_GRASSLAND       = bwg("amaranth_grassland");
    private static final RegistryKey<Biome> BWG_ARAUCARIA_SAVANNA        = bwg("araucaria_savanna");
    private static final RegistryKey<Biome> BWG_ASPEN_BOREAL             = bwg("aspen_boreal");
    private static final RegistryKey<Biome> BWG_ATACAMA_OUTBACK          = bwg("atacama_outback");
    private static final RegistryKey<Biome> BWG_BAOBAB_SAVANNA           = bwg("baobab_savanna");
    private static final RegistryKey<Biome> BWG_BASALT_BARRERA           = bwg("basalt_barrera");
    private static final RegistryKey<Biome> BWG_BAYOU                    = bwg("bayou");
    private static final RegistryKey<Biome> BWG_BLACK_FOREST             = bwg("black_forest");
    private static final RegistryKey<Biome> BWG_CANADIAN_SHIELD          = bwg("canadian_shield");
    private static final RegistryKey<Biome> BWG_CIKA_WOODS               = bwg("cika_woods");
    private static final RegistryKey<Biome> BWG_COCONINO_MEADOW          = bwg("coconino_meadow");
    private static final RegistryKey<Biome> BWG_CONIFEROUS_FOREST        = bwg("coniferous_forest");
    private static final RegistryKey<Biome> BWG_CRAG_GARDENS             = bwg("crag_gardens");
    private static final RegistryKey<Biome> BWG_CRIMSON_TUNDRA           = bwg("crimson_tundra");
    private static final RegistryKey<Biome> BWG_CYPRESS_SWAMPLANDS       = bwg("cypress_swamplands");
    private static final RegistryKey<Biome> BWG_CYPRESS_WETLANDS         = bwg("cypress_wetlands");
    private static final RegistryKey<Biome> BWG_DACITE_RIDGES            = bwg("dacite_ridges");
    private static final RegistryKey<Biome> BWG_DACITE_SHORE             = bwg("dacite_shore");
    private static final RegistryKey<Biome> BWG_DEAD_SEA                 = bwg("dead_sea");
    private static final RegistryKey<Biome> BWG_EBONY_WOODS              = bwg("ebony_woods");
    private static final RegistryKey<Biome> BWG_ENCHANTED_TANGLE         = bwg("enchanted_tangle");
    private static final RegistryKey<Biome> BWG_ERODED_BOREALIS          = bwg("eroded_borealis");
    private static final RegistryKey<Biome> BWG_FIRECRACKER_CHAPARRAL    = bwg("firecracker_chaparral");
    private static final RegistryKey<Biome> BWG_FORGOTTEN_FOREST         = bwg("forgotten_forest");
    private static final RegistryKey<Biome> BWG_FRAGMENT_JUNGLE          = bwg("fragment_jungle");
    private static final RegistryKey<Biome> BWG_FROSTED_CONIFEROUS_FOREST = bwg("frosted_coniferous_forest");
    private static final RegistryKey<Biome> BWG_FROSTED_TAIGA            = bwg("frosted_taiga");
    private static final RegistryKey<Biome> BWG_HOWLING_PEAKS            = bwg("howling_peaks");
    private static final RegistryKey<Biome> BWG_IRONWOOD_GOUR            = bwg("ironwood_gour");
    private static final RegistryKey<Biome> BWG_JACARANDA_JUNGLE         = bwg("jacaranda_jungle");
    private static final RegistryKey<Biome> BWG_LUSH_STACKS              = bwg("lush_stacks");
    private static final RegistryKey<Biome> BWG_MAPLE_TAIGA              = bwg("maple_taiga");
    private static final RegistryKey<Biome> BWG_MOJAVE_DESERT            = bwg("mojave_desert");
    private static final RegistryKey<Biome> BWG_ORCHARD                  = bwg("orchard");
    private static final RegistryKey<Biome> BWG_OVERGROWTH_WOODLANDS     = bwg("overgrowth_woodlands");
    private static final RegistryKey<Biome> BWG_PALE_BOG                 = bwg("pale_bog");
    private static final RegistryKey<Biome> BWG_PRAIRIE                  = bwg("prairie");
    private static final RegistryKey<Biome> BWG_PUMPKIN_VALLEY           = bwg("pumpkin_valley");
    private static final RegistryKey<Biome> BWG_RAINBOW_BEACH            = bwg("rainbow_beach");
    private static final RegistryKey<Biome> BWG_RED_ROCK_VALLEY          = bwg("red_rock_valley");
    private static final RegistryKey<Biome> BWG_RED_ROCK_PEAKS           = bwg("red_rock_peaks");
    private static final RegistryKey<Biome> BWG_REDWOOD_THICKET          = bwg("redwood_thicket");
    private static final RegistryKey<Biome> BWG_ROSE_FIELDS              = bwg("rose_fields");
    private static final RegistryKey<Biome> BWG_RUGGED_BADLANDS          = bwg("rugged_badlands");
    private static final RegistryKey<Biome> BWG_SAKURA_GROVE             = bwg("sakura_grove");
    private static final RegistryKey<Biome> BWG_SHATTERED_GLACIER        = bwg("shattered_glacier");
    private static final RegistryKey<Biome> BWG_SIERRA_BADLANDS          = bwg("sierra_badlands");
    private static final RegistryKey<Biome> BWG_SKYRIS_VALE              = bwg("skyris_vale");
    private static final RegistryKey<Biome> BWG_TROPICAL_RAINFOREST      = bwg("tropical_rainforest");
    private static final RegistryKey<Biome> BWG_TEMPERATE_GROVE          = bwg("temperate_grove");
    private static final RegistryKey<Biome> BWG_WEEPING_WITCH_FOREST     = bwg("weeping_witch_forest");
    private static final RegistryKey<Biome> BWG_WHITE_MANGROVE_MARSHES   = bwg("white_mangrove_marshes");
    private static final RegistryKey<Biome> BWG_WINDSWEPT_DESERT         = bwg("windswept_desert");
    private static final RegistryKey<Biome> BWG_ZELKOVA_FOREST           = bwg("zelkova_forest");

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

    private RegistryEntry<Biome> bwgOrFallback(RegistryKey<Biome> bwgKey, RegistryKey<Biome> fallback) {
        var opt = this.biomeLookup.getOptional(bwgKey);
        return opt.isPresent() ? opt.get() : this.biomeLookup.getOrThrow(fallback);
    }

    private void requireBiomeIdMap() {
        if (biomeIdMap == null) {
            Map<Short, RegistryEntry<Biome>> map = new HashMap<>();
            // Vanilla biomes
            map.put((short)  1, this.biomeLookup.getOrThrow(BiomeKeys.PLAINS));
            map.put((short)  3, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_PLAINS));
            map.put((short)  5, this.biomeLookup.getOrThrow(BiomeKeys.DESERT));
            map.put((short)  6, this.biomeLookup.getOrThrow(BiomeKeys.SWAMP));
            map.put((short)  8, this.biomeLookup.getOrThrow(BiomeKeys.FOREST));
            map.put((short) 15, this.biomeLookup.getOrThrow(BiomeKeys.TAIGA));
            map.put((short) 16, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_TAIGA));
            map.put((short) 17, this.biomeLookup.getOrThrow(BiomeKeys.SAVANNA));
            map.put((short) 19, this.biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_HILLS));
            map.put((short) 23, this.biomeLookup.getOrThrow(BiomeKeys.JUNGLE));
            map.put((short) 26, this.biomeLookup.getOrThrow(BiomeKeys.BADLANDS));
            map.put((short) 29, this.biomeLookup.getOrThrow(BiomeKeys.MEADOW));
            map.put((short) 31, this.biomeLookup.getOrThrow(BiomeKeys.GROVE));
            map.put((short) 32, this.biomeLookup.getOrThrow(BiomeKeys.SNOWY_SLOPES));
            map.put((short) 33, this.biomeLookup.getOrThrow(BiomeKeys.FROZEN_PEAKS));
            map.put((short) 35, this.biomeLookup.getOrThrow(BiomeKeys.STONY_PEAKS));
            map.put((short) 41, this.biomeLookup.getOrThrow(BiomeKeys.WARM_OCEAN));
            map.put((short) 44, this.biomeLookup.getOrThrow(BiomeKeys.OCEAN));
            map.put((short) 46, this.biomeLookup.getOrThrow(BiomeKeys.COLD_OCEAN));
            map.put((short) 48, this.biomeLookup.getOrThrow(BiomeKeys.FROZEN_OCEAN));
            map.put((short) 108, this.biomeLookup.getOrThrow(FOREST_SPARSE));
            map.put((short) 115, this.biomeLookup.getOrThrow(TAIGA_SPARSE));
            map.put((short) 116, this.biomeLookup.getOrThrow(SNOWY_TAIGA_SPARSE));
            // BWG biomes – fall back to nearest vanilla equivalent if mod is not loaded
            map.put((short) 200, bwgOrFallback(BWG_ALLIUM_SHRUBLAND,          BiomeKeys.PLAINS));
            map.put((short) 201, bwgOrFallback(BWG_AMARANTH_GRASSLAND,        BiomeKeys.DESERT));
            map.put((short) 202, bwgOrFallback(BWG_ARAUCARIA_SAVANNA,         BiomeKeys.SAVANNA));
            map.put((short) 203, bwgOrFallback(BWG_ASPEN_BOREAL,              BiomeKeys.TAIGA));
            map.put((short) 204, bwgOrFallback(BWG_ATACAMA_OUTBACK,           BiomeKeys.DESERT));
            map.put((short) 205, bwgOrFallback(BWG_BAOBAB_SAVANNA,            BiomeKeys.SAVANNA));
            map.put((short) 206, bwgOrFallback(BWG_BASALT_BARRERA,            BiomeKeys.WINDSWEPT_HILLS));
            map.put((short) 207, bwgOrFallback(BWG_BAYOU,                     BiomeKeys.SWAMP));
            map.put((short) 208, bwgOrFallback(BWG_BLACK_FOREST,              BiomeKeys.FOREST));
            map.put((short) 209, bwgOrFallback(BWG_CANADIAN_SHIELD,           BiomeKeys.GROVE));
            map.put((short) 210, bwgOrFallback(BWG_CIKA_WOODS,                BiomeKeys.FOREST));
            map.put((short) 211, bwgOrFallback(BWG_COCONINO_MEADOW,           BiomeKeys.PLAINS));
            map.put((short) 212, bwgOrFallback(BWG_CONIFEROUS_FOREST,         BiomeKeys.TAIGA));
            map.put((short) 213, bwgOrFallback(BWG_CRAG_GARDENS,              BiomeKeys.JUNGLE));
            map.put((short) 214, bwgOrFallback(BWG_CRIMSON_TUNDRA,            BiomeKeys.SNOWY_PLAINS));
            map.put((short) 215, bwgOrFallback(BWG_CYPRESS_SWAMPLANDS,        BiomeKeys.SWAMP));
            map.put((short) 216, bwgOrFallback(BWG_CYPRESS_WETLANDS,          BiomeKeys.SWAMP));
            map.put((short) 217, bwgOrFallback(BWG_DACITE_RIDGES,             BiomeKeys.STONY_PEAKS));
            map.put((short) 218, bwgOrFallback(BWG_DACITE_SHORE,              BiomeKeys.WARM_OCEAN));
            map.put((short) 219, bwgOrFallback(BWG_DEAD_SEA,                  BiomeKeys.WARM_OCEAN));
            map.put((short) 220, bwgOrFallback(BWG_EBONY_WOODS,               BiomeKeys.FOREST));
            map.put((short) 221, bwgOrFallback(BWG_ENCHANTED_TANGLE,          BiomeKeys.JUNGLE));
            map.put((short) 222, bwgOrFallback(BWG_ERODED_BOREALIS,           BiomeKeys.GROVE));
            map.put((short) 223, bwgOrFallback(BWG_FIRECRACKER_CHAPARRAL,     BiomeKeys.SAVANNA));
            map.put((short) 224, bwgOrFallback(BWG_FORGOTTEN_FOREST,          BiomeKeys.FOREST));
            map.put((short) 225, bwgOrFallback(BWG_FRAGMENT_JUNGLE,           BiomeKeys.JUNGLE));
            map.put((short) 226, bwgOrFallback(BWG_FROSTED_CONIFEROUS_FOREST, BiomeKeys.SNOWY_TAIGA));
            map.put((short) 227, bwgOrFallback(BWG_FROSTED_TAIGA,             BiomeKeys.SNOWY_TAIGA));
            map.put((short) 228, bwgOrFallback(BWG_HOWLING_PEAKS,             BiomeKeys.STONY_PEAKS));
            map.put((short) 229, bwgOrFallback(BWG_IRONWOOD_GOUR,             BiomeKeys.SAVANNA));
            map.put((short) 230, bwgOrFallback(BWG_JACARANDA_JUNGLE,          BiomeKeys.JUNGLE));
            map.put((short) 231, bwgOrFallback(BWG_LUSH_STACKS,               BiomeKeys.FOREST));
            map.put((short) 232, bwgOrFallback(BWG_MAPLE_TAIGA,               BiomeKeys.TAIGA));
            map.put((short) 233, bwgOrFallback(BWG_MOJAVE_DESERT,             BiomeKeys.DESERT));
            map.put((short) 234, bwgOrFallback(BWG_ORCHARD,                   BiomeKeys.FOREST));
            map.put((short) 235, bwgOrFallback(BWG_OVERGROWTH_WOODLANDS,      BiomeKeys.FOREST));
            map.put((short) 236, bwgOrFallback(BWG_PALE_BOG,                  BiomeKeys.SWAMP));
            map.put((short) 237, bwgOrFallback(BWG_PRAIRIE,                   BiomeKeys.DESERT));
            map.put((short) 238, bwgOrFallback(BWG_PUMPKIN_VALLEY,            BiomeKeys.PLAINS));
            map.put((short) 239, bwgOrFallback(BWG_RAINBOW_BEACH,             BiomeKeys.WARM_OCEAN));
            map.put((short) 240, bwgOrFallback(BWG_RED_ROCK_VALLEY,           BiomeKeys.DESERT));
            map.put((short) 241, bwgOrFallback(BWG_RED_ROCK_PEAKS,            BiomeKeys.STONY_PEAKS));
            map.put((short) 242, bwgOrFallback(BWG_REDWOOD_THICKET,           BiomeKeys.TAIGA));
            map.put((short) 243, bwgOrFallback(BWG_ROSE_FIELDS,               BiomeKeys.PLAINS));
            map.put((short) 244, bwgOrFallback(BWG_RUGGED_BADLANDS,           BiomeKeys.BADLANDS));
            map.put((short) 245, bwgOrFallback(BWG_SAKURA_GROVE,              BiomeKeys.FOREST));
            map.put((short) 246, bwgOrFallback(BWG_SHATTERED_GLACIER,         BiomeKeys.FROZEN_PEAKS));
            map.put((short) 247, bwgOrFallback(BWG_SIERRA_BADLANDS,           BiomeKeys.BADLANDS));
            map.put((short) 248, bwgOrFallback(BWG_SKYRIS_VALE,               BiomeKeys.PLAINS));
            map.put((short) 249, bwgOrFallback(BWG_TROPICAL_RAINFOREST,       BiomeKeys.JUNGLE));
            map.put((short) 250, bwgOrFallback(BWG_TEMPERATE_GROVE,           BiomeKeys.FOREST));
            map.put((short) 251, bwgOrFallback(BWG_WEEPING_WITCH_FOREST,      BiomeKeys.FOREST));
            map.put((short) 252, bwgOrFallback(BWG_WHITE_MANGROVE_MARSHES,    BiomeKeys.SWAMP));
            map.put((short) 253, bwgOrFallback(BWG_WINDSWEPT_DESERT,          BiomeKeys.DESERT));
            map.put((short) 254, bwgOrFallback(BWG_ZELKOVA_FOREST,            BiomeKeys.FOREST));
            biomeIdMap = map;
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

