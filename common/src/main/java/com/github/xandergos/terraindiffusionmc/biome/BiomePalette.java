package com.github.xandergos.terraindiffusionmc.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central biome palette used by the pipeline classifier and by the Minecraft biome source.
 *
 * <p>The short IDs are the internal wire format stored in generated heightmap tiles. For
 * Minecraft's pre-1.21.4 vanilla biomes, IDs follow the vanilla Java numeric order for easier
 * debugging. Pale Garden has no stable legacy numeric slot in that list, so it uses the next
 * internal ID.</p>
 */
public final class BiomePalette {
    private BiomePalette() {}

    public record Entry(short id, String name, ResourceKey<Biome> key) {}

    // Full Minecraft Java 1.21.11 vanilla biome registry.
    public static final short THE_VOID = 0;
    public static final short PLAINS = 1;
    public static final short SUNFLOWER_PLAINS = 2;
    public static final short SNOWY_PLAINS = 3;
    public static final short ICE_SPIKES = 4;
    public static final short DESERT = 5;
    public static final short SWAMP = 6;
    public static final short MANGROVE_SWAMP = 7;
    public static final short FOREST = 8;
    public static final short FLOWER_FOREST = 9;
    public static final short BIRCH_FOREST = 10;
    public static final short DARK_FOREST = 11;
    public static final short OLD_GROWTH_BIRCH_FOREST = 12;
    public static final short OLD_GROWTH_PINE_TAIGA = 13;
    public static final short OLD_GROWTH_SPRUCE_TAIGA = 14;
    public static final short TAIGA = 15;
    public static final short SNOWY_TAIGA = 16;
    public static final short SAVANNA = 17;
    public static final short SAVANNA_PLATEAU = 18;
    public static final short WINDSWEPT_HILLS = 19;
    public static final short WINDSWEPT_GRAVELLY_HILLS = 20;
    public static final short WINDSWEPT_FOREST = 21;
    public static final short WINDSWEPT_SAVANNA = 22;
    public static final short JUNGLE = 23;
    public static final short SPARSE_JUNGLE = 24;
    public static final short BAMBOO_JUNGLE = 25;
    public static final short BADLANDS = 26;
    public static final short ERODED_BADLANDS = 27;
    public static final short WOODED_BADLANDS = 28;
    public static final short MEADOW = 29;
    public static final short CHERRY_GROVE = 30;
    public static final short GROVE = 31;
    public static final short SNOWY_SLOPES = 32;
    public static final short FROZEN_PEAKS = 33;
    public static final short JAGGED_PEAKS = 34;
    public static final short STONY_PEAKS = 35;
    public static final short RIVER = 36;
    public static final short FROZEN_RIVER = 37;
    public static final short BEACH = 38;
    public static final short SNOWY_BEACH = 39;
    public static final short STONY_SHORE = 40;
    public static final short WARM_OCEAN = 41;
    public static final short LUKEWARM_OCEAN = 42;
    public static final short DEEP_LUKEWARM_OCEAN = 43;
    public static final short OCEAN = 44;
    public static final short DEEP_OCEAN = 45;
    public static final short COLD_OCEAN = 46;
    public static final short DEEP_COLD_OCEAN = 47;
    public static final short FROZEN_OCEAN = 48;
    public static final short DEEP_FROZEN_OCEAN = 49;
    public static final short MUSHROOM_FIELDS = 50;
    public static final short DRIPSTONE_CAVES = 51;
    public static final short LUSH_CAVES = 52;
    public static final short DEEP_DARK = 53;
    public static final short NETHER_WASTES = 54;
    public static final short WARPED_FOREST = 55;
    public static final short CRIMSON_FOREST = 56;
    public static final short SOUL_SAND_VALLEY = 57;
    public static final short BASALT_DELTAS = 58;
    public static final short THE_END = 59;
    public static final short END_HIGHLANDS = 60;
    public static final short END_MIDLANDS = 61;
    public static final short SMALL_END_ISLANDS = 62;
    public static final short END_BARRENS = 63;
    public static final short PALE_GARDEN = 64;

    // Terrain Diffusion helper biomes.
    public static final short FOREST_SPARSE = 108;
    public static final short TAIGA_SPARSE = 115;
    public static final short SNOWY_TAIGA_SPARSE = 116;

    public static final short DEFAULT = PLAINS;

    public static final Entry[] ENTRIES = new Entry[] {
            vanilla(THE_VOID, "the_void"),
            vanilla(PLAINS, "plains"),
            vanilla(SUNFLOWER_PLAINS, "sunflower_plains"),
            vanilla(SNOWY_PLAINS, "snowy_plains"),
            vanilla(ICE_SPIKES, "ice_spikes"),
            vanilla(DESERT, "desert"),
            vanilla(SWAMP, "swamp"),
            vanilla(MANGROVE_SWAMP, "mangrove_swamp"),
            vanilla(FOREST, "forest"),
            vanilla(FLOWER_FOREST, "flower_forest"),
            vanilla(BIRCH_FOREST, "birch_forest"),
            vanilla(DARK_FOREST, "dark_forest"),
            vanilla(OLD_GROWTH_BIRCH_FOREST, "old_growth_birch_forest"),
            vanilla(OLD_GROWTH_PINE_TAIGA, "old_growth_pine_taiga"),
            vanilla(OLD_GROWTH_SPRUCE_TAIGA, "old_growth_spruce_taiga"),
            vanilla(TAIGA, "taiga"),
            vanilla(SNOWY_TAIGA, "snowy_taiga"),
            vanilla(SAVANNA, "savanna"),
            vanilla(SAVANNA_PLATEAU, "savanna_plateau"),
            vanilla(WINDSWEPT_HILLS, "windswept_hills"),
            vanilla(WINDSWEPT_GRAVELLY_HILLS, "windswept_gravelly_hills"),
            vanilla(WINDSWEPT_FOREST, "windswept_forest"),
            vanilla(WINDSWEPT_SAVANNA, "windswept_savanna"),
            vanilla(JUNGLE, "jungle"),
            vanilla(SPARSE_JUNGLE, "sparse_jungle"),
            vanilla(BAMBOO_JUNGLE, "bamboo_jungle"),
            vanilla(BADLANDS, "badlands"),
            vanilla(ERODED_BADLANDS, "eroded_badlands"),
            vanilla(WOODED_BADLANDS, "wooded_badlands"),
            vanilla(MEADOW, "meadow"),
            vanilla(CHERRY_GROVE, "cherry_grove"),
            vanilla(GROVE, "grove"),
            vanilla(SNOWY_SLOPES, "snowy_slopes"),
            vanilla(FROZEN_PEAKS, "frozen_peaks"),
            vanilla(JAGGED_PEAKS, "jagged_peaks"),
            vanilla(STONY_PEAKS, "stony_peaks"),
            vanilla(RIVER, "river"),
            vanilla(FROZEN_RIVER, "frozen_river"),
            vanilla(BEACH, "beach"),
            vanilla(SNOWY_BEACH, "snowy_beach"),
            vanilla(STONY_SHORE, "stony_shore"),
            vanilla(WARM_OCEAN, "warm_ocean"),
            vanilla(LUKEWARM_OCEAN, "lukewarm_ocean"),
            vanilla(DEEP_LUKEWARM_OCEAN, "deep_lukewarm_ocean"),
            vanilla(OCEAN, "ocean"),
            vanilla(DEEP_OCEAN, "deep_ocean"),
            vanilla(COLD_OCEAN, "cold_ocean"),
            vanilla(DEEP_COLD_OCEAN, "deep_cold_ocean"),
            vanilla(FROZEN_OCEAN, "frozen_ocean"),
            vanilla(DEEP_FROZEN_OCEAN, "deep_frozen_ocean"),
            vanilla(MUSHROOM_FIELDS, "mushroom_fields"),
            vanilla(DRIPSTONE_CAVES, "dripstone_caves"),
            vanilla(LUSH_CAVES, "lush_caves"),
            vanilla(DEEP_DARK, "deep_dark"),
            vanilla(NETHER_WASTES, "nether_wastes"),
            vanilla(WARPED_FOREST, "warped_forest"),
            vanilla(CRIMSON_FOREST, "crimson_forest"),
            vanilla(SOUL_SAND_VALLEY, "soul_sand_valley"),
            vanilla(BASALT_DELTAS, "basalt_deltas"),
            vanilla(THE_END, "the_end"),
            vanilla(END_HIGHLANDS, "end_highlands"),
            vanilla(END_MIDLANDS, "end_midlands"),
            vanilla(SMALL_END_ISLANDS, "small_end_islands"),
            vanilla(END_BARRENS, "end_barrens"),
            vanilla(PALE_GARDEN, "pale_garden"),
            mod(FOREST_SPARSE, "forest_sparse"),
            mod(TAIGA_SPARSE, "taiga_sparse"),
            mod(SNOWY_TAIGA_SPARSE, "snowy_taiga_sparse")
    };

    public static Map<Short, Holder<Biome>> buildHolderMap(HolderGetter<Biome> biomeLookup) {
        Map<Short, Holder<Biome>> result = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            result.put(entry.id(), biomeLookup.getOrThrow(entry.key()));
        }
        return Map.copyOf(result);
    }

    public static String nameOf(short id) {
        for (Entry entry : ENTRIES) {
            if (entry.id() == id) return entry.name();
        }
        return "unknown_" + id;
    }

    private static Entry vanilla(short id, String path) {
        return entry(id, "minecraft", path);
    }

    private static Entry mod(short id, String path) {
        return entry(id, "terrain-diffusion-mc", path);
    }

    private static Entry entry(short id, String namespace, String path) {
        return new Entry(id, path, ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(namespace, path)));
    }
}
