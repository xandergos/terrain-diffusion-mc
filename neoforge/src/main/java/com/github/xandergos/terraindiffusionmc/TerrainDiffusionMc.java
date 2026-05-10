package com.github.xandergos.terraindiffusionmc;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TerrainDiffusionMc.FML_MOD_ID)
public class TerrainDiffusionMc {
    public static final String MOD_ID = "terrain-diffusion-mc";
    public static final String FML_MOD_ID = "terrain_diffusion_mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    public TerrainDiffusionMc(IEventBus modEventBus) {
        LOG.info("Initializing terrain-diffusion-mc for NeoForge");
        modEventBus.addListener(this::onRegister);
        TerrainDiffusionLifecycle.bootstrap(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get());

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }


    private void onRegister(RegisterEvent event) {
        event.register(Registries.BIOME_SOURCE, helper ->
                TerrainDiffusionLifecycle.registerBiomeSourceCodecs(helper::register));
        event.register(Registries.DENSITY_FUNCTION_TYPE, helper ->
                TerrainDiffusionLifecycle.registerDensityFunctionCodecs(helper::register));
    }

    private void onServerStarting(ServerStartingEvent event) {
        TerrainDiffusionLifecycle.onServerStarting();
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel world) {
            TerrainDiffusionLifecycle.onWorldLoad(world);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        TerrainDiffusionLifecycle.onServerStopping();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        TerrainDiffusionLifecycle.registerCommands(event.getDispatcher());
    }
}
