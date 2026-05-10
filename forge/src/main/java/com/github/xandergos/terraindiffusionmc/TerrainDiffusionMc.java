package com.github.xandergos.terraindiffusionmc;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TerrainDiffusionMc.FML_MOD_ID)
public class TerrainDiffusionMc {
    public static final String MOD_ID = "terrain-diffusion-mc";
    public static final String FML_MOD_ID = "terrain_diffusion_mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    public TerrainDiffusionMc(FMLJavaModLoadingContext context) {
        LOG.info("Initializing terrain-diffusion-mc for Forge");
        RegisterEvent.getBus(context.getModBusGroup()).addListener(this::onRegister);
        TerrainDiffusionLifecycle.bootstrap(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get());

        ServerStartingEvent.BUS.addListener(this::onServerStarting);
        LevelEvent.Load.BUS.addListener(this::onLevelLoad);
        ServerStoppingEvent.BUS.addListener(this::onServerStopping);
        RegisterCommandsEvent.BUS.addListener(this::onRegisterCommands);
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
