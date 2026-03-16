package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.PipelineModels;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionDensityFunction;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerrainDiffusionMc implements ModInitializer {
    public static final String MOD_ID = "terrain-diffusion-mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    @Override
    public void onInitialize() {
        LOG.info("Initializing terrain-diffusion-mc");
        Registry.register(Registries.BIOME_SOURCE, Identifier.of(MOD_ID, "terrain_diffusion"), TerrainDiffusionBiomeSource.CODEC);
        Registry.register(Registries.DENSITY_FUNCTION_TYPE, Identifier.of(MOD_ID, "terrain_diffusion"), TerrainDiffusionDensityFunction.CODEC);

        PipelineModels.load();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> LocalTerrainProvider.clearCache());

        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                LocalTerrainProvider.init(world.getSeed());
            }
        });
    }
}
