package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeClassifier;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TerrainDiffusionBiomeSource extends BiomeSource {
    private static final ResourceKey<Biome> FOREST_SPARSE = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("terrain-diffusion-mc", "forest_sparse"));
    private static final ResourceKey<Biome> TAIGA_SPARSE = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("terrain-diffusion-mc", "taiga_sparse"));
    private static final ResourceKey<Biome> SNOWY_TAIGA_SPARSE = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("terrain-diffusion-mc", "snowy_taiga_sparse"));

    // Oh The Biomes We've Gone biome keys
    private static ResourceKey<Biome> bwg(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("biomeswevegone", name));
    }
    private static final ResourceKey<Biome> BWG_ALLIUM_SHRUBLAND         = bwg("allium_shrubland");
    private static final ResourceKey<Biome> BWG_AMARANTH_GRASSLAND       = bwg("amaranth_grassland");
    private static final ResourceKey<Biome> BWG_ARAUCARIA_SAVANNA        = bwg("araucaria_savanna");
    private static final ResourceKey<Biome> BWG_ASPEN_BOREAL             = bwg("aspen_boreal");
    private static final ResourceKey<Biome> BWG_ATACAMA_OUTBACK          = bwg("atacama_outback");
    private static final ResourceKey<Biome> BWG_BAOBAB_SAVANNA           = bwg("baobab_savanna");
    private static final ResourceKey<Biome> BWG_BASALT_BARRERA           = bwg("basalt_barrera");
    private static final ResourceKey<Biome> BWG_BAYOU                    = bwg("bayou");
    private static final ResourceKey<Biome> BWG_BLACK_FOREST             = bwg("black_forest");
    private static final ResourceKey<Biome> BWG_CANADIAN_SHIELD          = bwg("canadian_shield");
    private static final ResourceKey<Biome> BWG_CIKA_WOODS               = bwg("cika_woods");
    private static final ResourceKey<Biome> BWG_COCONINO_MEADOW          = bwg("coconino_meadow");
    private static final ResourceKey<Biome> BWG_CONIFEROUS_FOREST        = bwg("coniferous_forest");
    private static final ResourceKey<Biome> BWG_CRAG_GARDENS             = bwg("crag_gardens");
    private static final ResourceKey<Biome> BWG_CRIMSON_TUNDRA           = bwg("crimson_tundra");
    private static final ResourceKey<Biome> BWG_CYPRESS_SWAMPLANDS       = bwg("cypress_swamplands");
    private static final ResourceKey<Biome> BWG_CYPRESS_WETLANDS         = bwg("cypress_wetlands");
    private static final ResourceKey<Biome> BWG_DACITE_RIDGES            = bwg("dacite_ridges");
    private static final ResourceKey<Biome> BWG_DACITE_SHORE             = bwg("dacite_shore");
    private static final ResourceKey<Biome> BWG_DEAD_SEA                 = bwg("dead_sea");
    private static final ResourceKey<Biome> BWG_EBONY_WOODS              = bwg("ebony_woods");
    private static final ResourceKey<Biome> BWG_ENCHANTED_TANGLE         = bwg("enchanted_tangle");
    private static final ResourceKey<Biome> BWG_ERODED_BOREALIS          = bwg("eroded_borealis");
    private static final ResourceKey<Biome> BWG_FIRECRACKER_CHAPARRAL    = bwg("firecracker_chaparral");
    private static final ResourceKey<Biome> BWG_FORGOTTEN_FOREST         = bwg("forgotten_forest");
    private static final ResourceKey<Biome> BWG_FRAGMENT_JUNGLE          = bwg("fragment_jungle");
    private static final ResourceKey<Biome> BWG_FROSTED_CONIFEROUS_FOREST = bwg("frosted_coniferous_forest");
    private static final ResourceKey<Biome> BWG_FROSTED_TAIGA            = bwg("frosted_taiga");
    private static final ResourceKey<Biome> BWG_HOWLING_PEAKS            = bwg("howling_peaks");
    private static final ResourceKey<Biome> BWG_IRONWOOD_GOUR            = bwg("ironwood_gour");
    private static final ResourceKey<Biome> BWG_JACARANDA_JUNGLE         = bwg("jacaranda_jungle");
    private static final ResourceKey<Biome> BWG_LUSH_STACKS              = bwg("lush_stacks");
    private static final ResourceKey<Biome> BWG_MAPLE_TAIGA              = bwg("maple_taiga");
    private static final ResourceKey<Biome> BWG_MOJAVE_DESERT            = bwg("mojave_desert");
    private static final ResourceKey<Biome> BWG_ORCHARD                  = bwg("orchard");
    private static final ResourceKey<Biome> BWG_OVERGROWTH_WOODLANDS     = bwg("overgrowth_woodlands");
    private static final ResourceKey<Biome> BWG_PALE_BOG                 = bwg("pale_bog");
    private static final ResourceKey<Biome> BWG_PRAIRIE                  = bwg("prairie");
    private static final ResourceKey<Biome> BWG_PUMPKIN_VALLEY           = bwg("pumpkin_valley");
    private static final ResourceKey<Biome> BWG_RAINBOW_BEACH            = bwg("rainbow_beach");
    private static final ResourceKey<Biome> BWG_RED_ROCK_VALLEY          = bwg("red_rock_valley");
    private static final ResourceKey<Biome> BWG_RED_ROCK_PEAKS           = bwg("red_rock_peaks");
    private static final ResourceKey<Biome> BWG_REDWOOD_THICKET          = bwg("redwood_thicket");
    private static final ResourceKey<Biome> BWG_ROSE_FIELDS              = bwg("rose_fields");
    private static final ResourceKey<Biome> BWG_RUGGED_BADLANDS          = bwg("rugged_badlands");
    private static final ResourceKey<Biome> BWG_SAKURA_GROVE             = bwg("sakura_grove");
    private static final ResourceKey<Biome> BWG_SHATTERED_GLACIER        = bwg("shattered_glacier");
    private static final ResourceKey<Biome> BWG_SIERRA_BADLANDS          = bwg("sierra_badlands");
    private static final ResourceKey<Biome> BWG_SKYRIS_VALE              = bwg("skyris_vale");
    private static final ResourceKey<Biome> BWG_TROPICAL_RAINFOREST      = bwg("tropical_rainforest");
    private static final ResourceKey<Biome> BWG_TEMPERATE_GROVE          = bwg("temperate_grove");
    private static final ResourceKey<Biome> BWG_WEEPING_WITCH_FOREST     = bwg("weeping_witch_forest");
    private static final ResourceKey<Biome> BWG_WHITE_MANGROVE_MARSHES   = bwg("white_mangrove_marshes");
    private static final ResourceKey<Biome> BWG_WINDSWEPT_DESERT         = bwg("windswept_desert");
    private static final ResourceKey<Biome> BWG_ZELKOVA_FOREST           = bwg("zelkova_forest");

    private static final int TILE_SIZE  = TerrainDiffusionConfig.tileSize();
    private static final int TILE_SHIFT = Integer.numberOfTrailingZeros(TILE_SIZE);

    /**
     * Per-thread single-entry cache: last surface biome lookup keyed by (blockX, blockZ).
     *
     * <p>The biome registry samples per quart-Y for every (x, z) column — typically 200+
     * Y levels per chunk per column. The surface biome itself is column-only (no Y
     * dependency once cave biomes are dispatched), so all those calls return the same
     * entry. Caching the last result skips the heightmap fetch + biomeIdMap lookup for
     * every Y after the first.
     */
    private static final ThreadLocal<SurfaceBiomeCache> SURFACE_BIOME_CACHE =
            ThreadLocal.withInitial(SurfaceBiomeCache::new);

    private static final class SurfaceBiomeCache {
        long key = Long.MIN_VALUE + 1;
        Holder<Biome> value;
    }

    public static final MapCodec<TerrainDiffusionBiomeSource> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(instance, instance.stable(TerrainDiffusionBiomeSource::new)));


    private final Map<Short, Holder<Biome>> biomeIdMap;

    public TerrainDiffusionBiomeSource(HolderGetter<Biome> biomeLookup) {
        // Resolve all biome holders eagerly in the constructor while biomeLookup is valid
        // (registry isn't frozen during codec decode). Storing the lookup for lazy use blew up at
        // server tick time when MappedRegistry's registration-lookup view tries to create a pending
        // holder reference for missing BWG biomes — registry is frozen by then and validateWrite
        // throws "Registry is already frozen".
        Map<Short, Holder<Biome>> map = new HashMap<>();
        // Vanilla biomes
        map.put((short)  1, biomeLookup.getOrThrow(Biomes.PLAINS));
        map.put((short)  3, biomeLookup.getOrThrow(Biomes.SNOWY_PLAINS));
        map.put((short)  5, biomeLookup.getOrThrow(Biomes.DESERT));
        map.put((short)  6, biomeLookup.getOrThrow(Biomes.SWAMP));
        map.put((short)  8, biomeLookup.getOrThrow(Biomes.FOREST));
        map.put((short) 15, biomeLookup.getOrThrow(Biomes.TAIGA));
        map.put((short) 16, biomeLookup.getOrThrow(Biomes.SNOWY_TAIGA));
        map.put((short) 17, biomeLookup.getOrThrow(Biomes.SAVANNA));
        map.put((short) 19, biomeLookup.getOrThrow(Biomes.WINDSWEPT_HILLS));
        map.put((short) 23, biomeLookup.getOrThrow(Biomes.JUNGLE));
        map.put((short) 26, biomeLookup.getOrThrow(Biomes.BADLANDS));
        map.put((short) 29, biomeLookup.getOrThrow(Biomes.MEADOW));
        map.put((short) 31, biomeLookup.getOrThrow(Biomes.GROVE));
        map.put((short) 32, biomeLookup.getOrThrow(Biomes.SNOWY_SLOPES));
        map.put((short) 33, biomeLookup.getOrThrow(Biomes.FROZEN_PEAKS));
        map.put((short) 35, biomeLookup.getOrThrow(Biomes.STONY_PEAKS));
        map.put((short) 41, biomeLookup.getOrThrow(Biomes.WARM_OCEAN));
        map.put((short) 44, biomeLookup.getOrThrow(Biomes.OCEAN));
        map.put((short) 46, biomeLookup.getOrThrow(Biomes.COLD_OCEAN));
        map.put((short) 48, biomeLookup.getOrThrow(Biomes.FROZEN_OCEAN));
        map.put((short) 108, biomeLookup.getOrThrow(FOREST_SPARSE));
        map.put((short) 115, biomeLookup.getOrThrow(TAIGA_SPARSE));
        map.put((short) 116, biomeLookup.getOrThrow(SNOWY_TAIGA_SPARSE));
        // Cave biomes
        map.put((short)  60, biomeLookup.getOrThrow(Biomes.LUSH_CAVES));
        map.put((short)  61, biomeLookup.getOrThrow(Biomes.DRIPSTONE_CAVES));
        map.put((short)  62, biomeLookup.getOrThrow(Biomes.DEEP_DARK));
        // BWG biomes – fall back to nearest vanilla equivalent if mod is not loaded
        map.put((short) 200, bwgOrFallback(biomeLookup, BWG_ALLIUM_SHRUBLAND,          Biomes.PLAINS));
        map.put((short) 201, bwgOrFallback(biomeLookup, BWG_AMARANTH_GRASSLAND,        Biomes.DESERT));
        map.put((short) 202, bwgOrFallback(biomeLookup, BWG_ARAUCARIA_SAVANNA,         Biomes.SAVANNA));
        map.put((short) 203, bwgOrFallback(biomeLookup, BWG_ASPEN_BOREAL,              Biomes.TAIGA));
        map.put((short) 204, bwgOrFallback(biomeLookup, BWG_ATACAMA_OUTBACK,           Biomes.DESERT));
        map.put((short) 205, bwgOrFallback(biomeLookup, BWG_BAOBAB_SAVANNA,            Biomes.SAVANNA));
        map.put((short) 206, bwgOrFallback(biomeLookup, BWG_BASALT_BARRERA,            Biomes.WINDSWEPT_HILLS));
        map.put((short) 207, bwgOrFallback(biomeLookup, BWG_BAYOU,                     Biomes.SWAMP));
        map.put((short) 208, bwgOrFallback(biomeLookup, BWG_BLACK_FOREST,              Biomes.FOREST));
        map.put((short) 209, bwgOrFallback(biomeLookup, BWG_CANADIAN_SHIELD,           Biomes.GROVE));
        map.put((short) 210, bwgOrFallback(biomeLookup, BWG_CIKA_WOODS,                Biomes.FOREST));
        map.put((short) 211, bwgOrFallback(biomeLookup, BWG_COCONINO_MEADOW,           Biomes.PLAINS));
        map.put((short) 212, bwgOrFallback(biomeLookup, BWG_CONIFEROUS_FOREST,         Biomes.TAIGA));
        map.put((short) 213, bwgOrFallback(biomeLookup, BWG_CRAG_GARDENS,              Biomes.JUNGLE));
        map.put((short) 214, bwgOrFallback(biomeLookup, BWG_CRIMSON_TUNDRA,            Biomes.SNOWY_PLAINS));
        map.put((short) 215, bwgOrFallback(biomeLookup, BWG_CYPRESS_SWAMPLANDS,        Biomes.SWAMP));
        map.put((short) 216, bwgOrFallback(biomeLookup, BWG_CYPRESS_WETLANDS,          Biomes.SWAMP));
        map.put((short) 217, bwgOrFallback(biomeLookup, BWG_DACITE_RIDGES,             Biomes.STONY_PEAKS));
        map.put((short) 218, bwgOrFallback(biomeLookup, BWG_DACITE_SHORE,              Biomes.WARM_OCEAN));
        map.put((short) 219, bwgOrFallback(biomeLookup, BWG_DEAD_SEA,                  Biomes.WARM_OCEAN));
        map.put((short) 220, bwgOrFallback(biomeLookup, BWG_EBONY_WOODS,               Biomes.FOREST));
        map.put((short) 221, bwgOrFallback(biomeLookup, BWG_ENCHANTED_TANGLE,          Biomes.JUNGLE));
        map.put((short) 222, bwgOrFallback(biomeLookup, BWG_ERODED_BOREALIS,           Biomes.GROVE));
        map.put((short) 223, bwgOrFallback(biomeLookup, BWG_FIRECRACKER_CHAPARRAL,     Biomes.SAVANNA));
        map.put((short) 224, bwgOrFallback(biomeLookup, BWG_FORGOTTEN_FOREST,          Biomes.FOREST));
        map.put((short) 225, bwgOrFallback(biomeLookup, BWG_FRAGMENT_JUNGLE,           Biomes.JUNGLE));
        map.put((short) 226, bwgOrFallback(biomeLookup, BWG_FROSTED_CONIFEROUS_FOREST, Biomes.SNOWY_TAIGA));
        map.put((short) 227, bwgOrFallback(biomeLookup, BWG_FROSTED_TAIGA,             Biomes.SNOWY_TAIGA));
        map.put((short) 228, bwgOrFallback(biomeLookup, BWG_HOWLING_PEAKS,             Biomes.STONY_PEAKS));
        map.put((short) 229, bwgOrFallback(biomeLookup, BWG_IRONWOOD_GOUR,             Biomes.SAVANNA));
        map.put((short) 230, bwgOrFallback(biomeLookup, BWG_JACARANDA_JUNGLE,          Biomes.JUNGLE));
        map.put((short) 231, bwgOrFallback(biomeLookup, BWG_LUSH_STACKS,               Biomes.FOREST));
        map.put((short) 232, bwgOrFallback(biomeLookup, BWG_MAPLE_TAIGA,               Biomes.TAIGA));
        map.put((short) 233, bwgOrFallback(biomeLookup, BWG_MOJAVE_DESERT,             Biomes.DESERT));
        map.put((short) 234, bwgOrFallback(biomeLookup, BWG_ORCHARD,                   Biomes.FOREST));
        map.put((short) 235, bwgOrFallback(biomeLookup, BWG_OVERGROWTH_WOODLANDS,      Biomes.FOREST));
        map.put((short) 236, bwgOrFallback(biomeLookup, BWG_PALE_BOG,                  Biomes.SWAMP));
        map.put((short) 237, bwgOrFallback(biomeLookup, BWG_PRAIRIE,                   Biomes.DESERT));
        map.put((short) 238, bwgOrFallback(biomeLookup, BWG_PUMPKIN_VALLEY,            Biomes.PLAINS));
        map.put((short) 239, bwgOrFallback(biomeLookup, BWG_RAINBOW_BEACH,             Biomes.WARM_OCEAN));
        map.put((short) 240, bwgOrFallback(biomeLookup, BWG_RED_ROCK_VALLEY,           Biomes.DESERT));
        map.put((short) 241, bwgOrFallback(biomeLookup, BWG_RED_ROCK_PEAKS,            Biomes.STONY_PEAKS));
        map.put((short) 242, bwgOrFallback(biomeLookup, BWG_REDWOOD_THICKET,           Biomes.TAIGA));
        map.put((short) 243, bwgOrFallback(biomeLookup, BWG_ROSE_FIELDS,               Biomes.PLAINS));
        map.put((short) 244, bwgOrFallback(biomeLookup, BWG_RUGGED_BADLANDS,           Biomes.BADLANDS));
        map.put((short) 245, bwgOrFallback(biomeLookup, BWG_SAKURA_GROVE,              Biomes.FOREST));
        map.put((short) 246, bwgOrFallback(biomeLookup, BWG_SHATTERED_GLACIER,         Biomes.FROZEN_PEAKS));
        map.put((short) 247, bwgOrFallback(biomeLookup, BWG_SIERRA_BADLANDS,           Biomes.BADLANDS));
        map.put((short) 248, bwgOrFallback(biomeLookup, BWG_SKYRIS_VALE,               Biomes.PLAINS));
        map.put((short) 249, bwgOrFallback(biomeLookup, BWG_TROPICAL_RAINFOREST,       Biomes.JUNGLE));
        map.put((short) 250, bwgOrFallback(biomeLookup, BWG_TEMPERATE_GROVE,           Biomes.FOREST));
        map.put((short) 251, bwgOrFallback(biomeLookup, BWG_WEEPING_WITCH_FOREST,      Biomes.FOREST));
        map.put((short) 252, bwgOrFallback(biomeLookup, BWG_WHITE_MANGROVE_MARSHES,    Biomes.SWAMP));
        map.put((short) 253, bwgOrFallback(biomeLookup, BWG_WINDSWEPT_DESERT,          Biomes.DESERT));
        map.put((short) 254, bwgOrFallback(biomeLookup, BWG_ZELKOVA_FOREST,            Biomes.FOREST));
        this.biomeIdMap = Map.copyOf(map);
    }

    private static Holder<Biome> bwgOrFallback(HolderGetter<Biome> lookup, ResourceKey<Biome> bwgKey, ResourceKey<Biome> fallback) {
        // Skip the lookup entirely if BWG isn't installed: the HolderGetter we receive during
        // codec decoding is a registration lookup, and calling get(key) on it interns a pending
        // Holder reference for the key. Those pending refs cause the biome registry's freeze
        // validation to fail with "Unbound values" when BWG biomes never get registered.
        if (!ModList.get().isLoaded("biomeswevegone")) {
            return lookup.getOrThrow(fallback);
        }
        var opt = lookup.get(bwgKey);
        return opt.isPresent() ? opt.get() : lookup.getOrThrow(fallback);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomeIdMap.values().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler noise) {
        Holder<Biome> defaultEntry = biomeIdMap.get((short) 1);

        // x, y, z are in quart coordinates (block / 4)
        int blockX = QuartPos.toBlock(x);
        int blockY = QuartPos.toBlock(y);
        int blockZ = QuartPos.toBlock(z);

        // Cave biome dispatch: only consider it for the underground band. The
        // classifier itself short-circuits above Y=30, but we also skip the
        // lookup entirely for high Y to avoid the noise call hot path.
        if (blockY <= 30) {
            short caveBiomeId = BiomeClassifier.classifyCaveBiome(blockX, blockY, blockZ);
            if (caveBiomeId >= 0) {
                Holder<Biome> caveBiome = biomeIdMap.get(caveBiomeId);
                if (caveBiome != null) return caveBiome;
            }
        }

        // Surface biome cache: same column, same biome regardless of Y. The chunk-gen
        // loop calls this method for every Y quart in the column, so a single-entry
        // cache absorbs ~200+ calls per column.
        long colKey = ((long) blockX << 32) | (blockZ & 0xFFFFFFFFL);
        SurfaceBiomeCache cache = SURFACE_BIOME_CACHE.get();
        if (cache.key == colKey) {
            return cache.value;
        }

        int tileX = blockX >> TILE_SHIFT;
        int tileZ = blockZ >> TILE_SHIFT;

        int blockStartX = tileX << TILE_SHIFT;
        int blockStartZ = tileZ << TILE_SHIFT;
        int blockEndX = blockStartX + TILE_SIZE;
        int blockEndZ = blockStartZ + TILE_SIZE;

        HeightmapData data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        Holder<Biome> result = defaultEntry;
        if (data != null && data.biomeIds != null) {
            int localX = Math.max(0, Math.min(data.width  - 1, blockX - blockStartX));
            int localZ = Math.max(0, Math.min(data.height - 1, blockZ - blockStartZ));
            Holder<Biome> entry = biomeIdMap.get(data.biomeIds[localZ][localX]);
            if (entry != null) result = entry;
        }

        cache.key = colKey;
        cache.value = result;
        return result;
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos origin, int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval, Predicate<Holder<Biome>> predicate, Climate.Sampler noiseSampler, LevelReader world) {
        return null;
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int x, int y, int z, int radius, int blockCheckInterval, Predicate<Holder<Biome>> predicate, RandomSource random, boolean bl, Climate.Sampler noiseSampler) {
        return null;
    }
}

