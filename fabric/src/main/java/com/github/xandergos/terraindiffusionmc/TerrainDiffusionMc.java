package com.github.xandergos.terraindiffusionmc;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerrainDiffusionMc implements ModInitializer {
    public static final String MOD_ID = "terrain-diffusion-mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    @Override
    public void onInitialize() {
        LOG.info("Initializing terrain-diffusion-mc");
        TerrainDiffusionLifecycle.registerBiomeSourceCodecs((id, codec) -> Registry.register(BuiltInRegistries.BIOME_SOURCE, id, codec));
        TerrainDiffusionLifecycle.registerDensityFunctionCodecs((id, codec) -> Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id, codec));
        TerrainDiffusionLifecycle.bootstrap(FabricLoader.getInstance().getConfigDir(), FabricLoader.getInstance().getGameDir());

        ServerLifecycleEvents.SERVER_STARTING.register(server -> TerrainDiffusionLifecycle.onServerStarting());
        ServerWorldEvents.LOAD.register((server, world) -> TerrainDiffusionLifecycle.onWorldLoad(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TerrainDiffusionLifecycle.onServerStopping());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TerrainDiffusionLifecycle.registerCommands(dispatcher));
    }
}
