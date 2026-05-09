package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.platform.PlatformPaths;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TerrainDiffusionMc.MOD_ID)
public class TerrainDiffusionMc {
    public static final String MOD_ID = "terrain-diffusion-mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    public TerrainDiffusionMc(IEventBus modEventBus) {
        PlatformPaths.configure(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get());
        LOG.warn("terrain-diffusion-mc NeoForge entrypoint is present. Common code has been moved to Mojang/Parchment mappings; NeoForge registration still needs loader-specific wiring.");
    }
}
