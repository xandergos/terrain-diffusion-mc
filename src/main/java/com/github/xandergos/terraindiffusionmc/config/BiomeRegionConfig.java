package com.github.xandergos.terraindiffusionmc.config;

import com.github.xandergos.terraindiffusionmc.pipeline.BiomeClassifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class BiomeRegionConfig {
    private static final Logger LOG = LoggerFactory.getLogger(BiomeRegionConfig.class);
    private static final String FILE_NAME = "biome-regions.json";
    private static final String RESOURCE_PATH = "/" + FILE_NAME;
    private static final String CONFIG_DIR_NAME = "terrain-diffusion-mc";
    private static final short FIRST_DYNAMIC_ID = 300;

    private static volatile List<BiomeRegion> loadedRegions = null;
    private static volatile Map<String, Short> allBiomeIds = null;

    /** ResourceLocation string → short ID for all pre-assigned biomes (vanilla, cave, TD-custom, BWG). */
    private static final Map<String, Short> KNOWN_BIOME_IDS = buildKnownIds();

    private static Map<String, Short> buildKnownIds() {
        Map<String, Short> m = new LinkedHashMap<>();
        // Vanilla
        m.put("minecraft:plains",           (short)   1);
        m.put("minecraft:snowy_plains",      (short)   3);
        m.put("minecraft:desert",            (short)   5);
        m.put("minecraft:swamp",             (short)   6);
        m.put("minecraft:forest",            (short)   8);
        m.put("minecraft:taiga",             (short)  15);
        m.put("minecraft:snowy_taiga",       (short)  16);
        m.put("minecraft:savanna",           (short)  17);
        m.put("minecraft:windswept_hills",   (short)  19);
        m.put("minecraft:jungle",            (short)  23);
        m.put("minecraft:badlands",          (short)  26);
        m.put("minecraft:meadow",            (short)  29);
        m.put("minecraft:grove",             (short)  31);
        m.put("minecraft:snowy_slopes",      (short)  32);
        m.put("minecraft:frozen_peaks",      (short)  33);
        m.put("minecraft:stony_peaks",       (short)  35);
        m.put("minecraft:warm_ocean",        (short)  41);
        m.put("minecraft:ocean",             (short)  44);
        m.put("minecraft:cold_ocean",        (short)  46);
        m.put("minecraft:frozen_ocean",      (short)  48);
        // Cave biomes
        m.put("minecraft:lush_caves",        (short)  60);
        m.put("minecraft:dripstone_caves",   (short)  61);
        m.put("minecraft:deep_dark",         (short)  62);
        // Custom TD biomes
        m.put("terrain-diffusion-mc:forest_sparse",       (short) 108);
        m.put("terrain-diffusion-mc:taiga_sparse",        (short) 115);
        m.put("terrain-diffusion-mc:snowy_taiga_sparse",  (short) 116);
        // BWG biomes (200–254)
        m.put("biomeswevegone:allium_shrubland",          (short) 200);
        m.put("biomeswevegone:amaranth_grassland",        (short) 201);
        m.put("biomeswevegone:araucaria_savanna",         (short) 202);
        m.put("biomeswevegone:aspen_boreal",              (short) 203);
        m.put("biomeswevegone:atacama_outback",           (short) 204);
        m.put("biomeswevegone:baobab_savanna",            (short) 205);
        m.put("biomeswevegone:basalt_barrera",            (short) 206);
        m.put("biomeswevegone:bayou",                     (short) 207);
        m.put("biomeswevegone:black_forest",              (short) 208);
        m.put("biomeswevegone:canadian_shield",           (short) 209);
        m.put("biomeswevegone:cika_woods",                (short) 210);
        m.put("biomeswevegone:coconino_meadow",           (short) 211);
        m.put("biomeswevegone:coniferous_forest",         (short) 212);
        m.put("biomeswevegone:crag_gardens",              (short) 213);
        m.put("biomeswevegone:crimson_tundra",            (short) 214);
        m.put("biomeswevegone:cypress_swamplands",        (short) 215);
        m.put("biomeswevegone:cypress_wetlands",          (short) 216);
        m.put("biomeswevegone:dacite_ridges",             (short) 217);
        m.put("biomeswevegone:dacite_shore",              (short) 218);
        m.put("biomeswevegone:dead_sea",                  (short) 219);
        m.put("biomeswevegone:ebony_woods",               (short) 220);
        m.put("biomeswevegone:enchanted_tangle",          (short) 221);
        m.put("biomeswevegone:eroded_borealis",           (short) 222);
        m.put("biomeswevegone:firecracker_chaparral",     (short) 223);
        m.put("biomeswevegone:forgotten_forest",          (short) 224);
        m.put("biomeswevegone:fragment_jungle",           (short) 225);
        m.put("biomeswevegone:frosted_coniferous_forest", (short) 226);
        m.put("biomeswevegone:frosted_taiga",             (short) 227);
        m.put("biomeswevegone:howling_peaks",             (short) 228);
        m.put("biomeswevegone:ironwood_gour",             (short) 229);
        m.put("biomeswevegone:jacaranda_jungle",          (short) 230);
        m.put("biomeswevegone:lush_stacks",               (short) 231);
        m.put("biomeswevegone:maple_taiga",               (short) 232);
        m.put("biomeswevegone:mojave_desert",             (short) 233);
        m.put("biomeswevegone:orchard",                   (short) 234);
        m.put("biomeswevegone:overgrowth_woodlands",      (short) 235);
        m.put("biomeswevegone:pale_bog",                  (short) 236);
        m.put("biomeswevegone:prairie",                   (short) 237);
        m.put("biomeswevegone:pumpkin_valley",            (short) 238);
        m.put("biomeswevegone:rainbow_beach",             (short) 239);
        m.put("biomeswevegone:red_rock_valley",           (short) 240);
        m.put("biomeswevegone:red_rock_peaks",            (short) 241);
        m.put("biomeswevegone:redwood_thicket",           (short) 242);
        m.put("biomeswevegone:rose_fields",               (short) 243);
        m.put("biomeswevegone:rugged_badlands",           (short) 244);
        m.put("biomeswevegone:sakura_grove",              (short) 245);
        m.put("biomeswevegone:shattered_glacier",         (short) 246);
        m.put("biomeswevegone:sierra_badlands",           (short) 247);
        m.put("biomeswevegone:skyris_vale",               (short) 248);
        m.put("biomeswevegone:tropical_rainforest",       (short) 249);
        m.put("biomeswevegone:temperate_grove",           (short) 250);
        m.put("biomeswevegone:weeping_witch_forest",      (short) 251);
        m.put("biomeswevegone:white_mangrove_marshes",    (short) 252);
        m.put("biomeswevegone:windswept_desert",          (short) 253);
        m.put("biomeswevegone:zelkova_forest",            (short) 254);
        return Collections.unmodifiableMap(m);
    }

    private BiomeRegionConfig() {}

    public static boolean isConfigured() {
        return loadedRegions != null;
    }

    /** Parse biome-regions.json and pre-assign IDs. Call once during FMLCommonSetupEvent. */
    public static void load() {
        BiomeRegionConfigJson config = readConfigJson();
        if (config == null) {
            loadedRegions = null;
            allBiomeIds = null;
            return;
        }

        // Apply noise settings even when no regions are defined.
        BiomeClassifier.configureNoise(config.cellNoiseScale, config.warpScale, config.warpAmplitude,
                config.warpOctaves, config.warpLacunarity, config.warpGain);

        List<BiomeRegion> regions = config.regions;
        if (regions == null || regions.isEmpty()) {
            loadedRegions = null;
            allBiomeIds = null;
            return;
        }

        // Assign stable IDs (300+) for any biome keys not already in KNOWN_BIOME_IDS.
        // Process in deterministic order so IDs are the same across repeated calls.
        Map<String, Short> ids = new LinkedHashMap<>(KNOWN_BIOME_IDS);
        short nextId = FIRST_DYNAMIC_ID;
        Set<String> newKeys = new LinkedHashSet<>();
        for (BiomeRegion region : regions) {
            if (region.biomes == null) continue;
            for (BiomeEntry entry : region.biomes) {
                if (entry.biome != null && !ids.containsKey(entry.biome)) {
                    newKeys.add(entry.biome);
                }
            }
        }
        for (String key : newKeys) {
            ids.put(key, nextId++);
        }

        loadedRegions = regions;
        allBiomeIds = Collections.unmodifiableMap(ids);
        LOG.info("[terrain-diffusion-mc] Loaded {} biome regions", regions.size());
    }

    public static final class BuildResult {
        public final BiomeClassifier.CompiledRegion[] regions;
        /** Biome holders for IDs that are not in the default biomeIdMap (IDs >= 300). */
        public final Map<Short, Holder<Biome>> additionalBiomes;

        BuildResult(BiomeClassifier.CompiledRegion[] regions, Map<Short, Holder<Biome>> additionalBiomes) {
            this.regions = regions;
            this.additionalBiomes = additionalBiomes;
        }
    }

    /**
     * Compile loaded regions into runtime form and resolve biome holders.
     * Call from the BiomeSource constructor while biomeLookup is still valid.
     * Returns null if no config was loaded.
     */
    public static BuildResult buildCompiled(HolderGetter<Biome> biomeLookup) {
        List<BiomeRegion> regions = loadedRegions;
        Map<String, Short> ids = allBiomeIds;
        if (regions == null || ids == null) return null;

        boolean bwgLoaded = ModList.get().isLoaded("biomeswevegone");

        BiomeClassifier.CompiledRegion[] compiled = new BiomeClassifier.CompiledRegion[regions.size()];
        for (int i = 0; i < regions.size(); i++) {
            compiled[i] = compileRegion(regions.get(i), ids);
        }

        // Only add holders for IDs >= FIRST_DYNAMIC_ID; the rest are already in biomeIdMap.
        // Guard: only call biomeLookup.get() for namespaces whose mod is actually loaded.
        // Calling it for an uninstalled mod's namespace interns a pending Holder reference
        // that causes "Unbound values" during registry freeze (same issue as bwgOrFallback).
        Map<Short, Holder<Biome>> extra = new HashMap<>();
        for (Map.Entry<String, Short> e : ids.entrySet()) {
            if (e.getValue() < FIRST_DYNAMIC_ID) continue;
            ResourceLocation loc = ResourceLocation.tryParse(e.getKey());
            if (loc == null) continue;
            String ns = loc.getNamespace();
            if (!ns.equals("minecraft") && !ns.equals("terrain-diffusion-mc")
                    && !ModList.get().isLoaded(ns)) continue;
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, loc);
            biomeLookup.get(key).ifPresent(holder -> extra.put(e.getValue(), holder));
        }

        return new BuildResult(compiled, extra);
    }

    private static BiomeClassifier.CompiledRegion compileRegion(BiomeRegion region, Map<String, Short> ids) {
        float[] min = new float[BiomeClassifier.VAR_COUNT];
        float[] max = new float[BiomeClassifier.VAR_COUNT];
        Arrays.fill(min, Float.NEGATIVE_INFINITY);
        Arrays.fill(max, Float.POSITIVE_INFINITY);

        if (region.conditions != null) {
            for (Map.Entry<String, BiomeRegion.RangeCondition> e : region.conditions.entrySet()) {
                int vi = BiomeClassifier.varIndex(e.getKey());
                if (vi < 0) {
                    LOG.warn("[terrain-diffusion-mc] Unknown condition variable '{}' in region '{}'",
                            e.getKey(), region.name);
                    continue;
                }
                min[vi] = e.getValue().min;
                max[vi] = e.getValue().max;
            }
        }

        int count = region.biomes == null ? 0 : region.biomes.size();
        short[] biomeIds  = new short[count];
        float[] priorities = new float[count];
        int j = 0;
        if (region.biomes != null) {
            for (BiomeEntry entry : region.biomes) {
                if (entry.biome == null) continue;
                Short id = ids.get(entry.biome);
                if (id == null) continue;
                biomeIds[j]   = id;
                priorities[j] = Math.max(0f, entry.priority);
                j++;
            }
        }
        if (j < count) {
            biomeIds   = Arrays.copyOf(biomeIds,   j);
            priorities = Arrays.copyOf(priorities, j);
        }

        return new BiomeClassifier.CompiledRegion(min, max, biomeIds, priorities);
    }

    private static BiomeRegionConfigJson readConfigJson() {
        Path configPath = resolveConfigPath();
        if (configPath != null) {
            if (Files.exists(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    return parseJson(in);
                } catch (IOException e) {
                    LOG.error("[terrain-diffusion-mc] Failed to read biome-regions.json: {}", e.getMessage());
                }
            } else {
                writeDefaultConfig(configPath);
            }
        }
        // Fall back to bundled resource (also reached on first run after writeDefaultConfig)
        try (InputStream in = BiomeRegionConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in != null) return parseJson(in);
        } catch (IOException e) {
            LOG.error("[terrain-diffusion-mc] Failed to load bundled biome-regions.json: {}", e.getMessage());
        }
        return null;
    }

    private static BiomeRegionConfigJson parseJson(InputStream in) throws IOException {
        Gson gson = new GsonBuilder().create();
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            BiomeRegionConfigJson config = gson.fromJson(reader, BiomeRegionConfigJson.class);
            return config != null ? config : new BiomeRegionConfigJson();
        }
    }

    private static Path resolveConfigPath() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR_NAME).resolve(FILE_NAME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void writeDefaultConfig(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            try (InputStream in = BiomeRegionConfig.class.getResourceAsStream(RESOURCE_PATH)) {
                if (in != null) Files.copy(in, configPath);
            }
        } catch (IOException e) {
            LOG.warn("[terrain-diffusion-mc] Failed to write default biome-regions.json: {}", e.getMessage());
        }
    }

    private static final class BiomeRegionConfigJson {
        float cellNoiseScale = 300f;
        float warpAmplitude  = 80f;
        float warpScale      = 200f;
        int   warpOctaves    = 2;
        float warpLacunarity = 2f;
        float warpGain       = 0.5f;
        List<BiomeRegion> regions;
    }
}
