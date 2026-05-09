package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.config.BiomeRegionConfig;
import com.github.xandergos.terraindiffusionmc.explorer.ExplorerServer;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.ModelAssetManager;
import com.github.xandergos.terraindiffusionmc.pipeline.PipelineModels;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionDensityFunction;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.literal;

// NeoForge modIds must match [a-z][a-z0-9_]+; the asset/data namespace keeps hyphens for
// compatibility with existing resource files at assets/terrain-diffusion-mc/ and
// data/terrain-diffusion-mc/.
@Mod("terrain_diffusion_mc")
public class TerrainDiffusionMc {
    public static final String MOD_ID = "terrain-diffusion-mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    public TerrainDiffusionMc(IEventBus modEventBus) {
        LOG.info("Initializing terrain-diffusion-mc");
        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(this::onCommonSetup);

        IEventBus gameEventBus = NeoForge.EVENT_BUS;
        gameEventBus.addListener(this::onServerStarting);
        gameEventBus.addListener(this::onServerStopping);
        gameEventBus.addListener(this::onLevelLoad);
        gameEventBus.addListener(this::onRegisterCommands);
    }

    private void onRegister(RegisterEvent event) {
        event.register(Registries.BIOME_SOURCE, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(MOD_ID, "terrain_diffusion"),
                        TerrainDiffusionBiomeSource.CODEC));
        event.register(Registries.DENSITY_FUNCTION_TYPE, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(MOD_ID, "terrain_diffusion"),
                        TerrainDiffusionDensityFunction.CODEC));
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        BiomeRegionConfig.load();
    }

    private void onServerStarting(ServerStartingEvent event) {
        LocalTerrainProvider.clearCache();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ExplorerServer.stop();
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.dimension() == Level.OVERWORLD) {
            WorldScaleManager.initializeForWorld(serverLevel);
            LocalTerrainProvider.init(serverLevel.getSeed());
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("td-explore").executes(TerrainDiffusionMc::executeExplore));
    }

    private static int executeExplore(CommandContext<CommandSourceStack> ctx) {
        try {
            int port = ExplorerServer.startIfNotRunning();
            String url = "http://localhost:" + port;
            MutableComponent link = Component.literal(url)
                    .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                     .withUnderlined(true));
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Terrain Explorer: ").append(link),
                    false);
        } catch (Exception e) {
            LOG.error("Failed to start terrain explorer", e);
            ctx.getSource().sendFailure(Component.literal("Failed to start terrain explorer: " + e.getMessage()));
        }
        return 1;
    }
}
