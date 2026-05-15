package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.config.BiomeRegionConfig;
import com.github.xandergos.terraindiffusionmc.explorer.ExplorerServer;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeClassifier;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.ModelAssetManager;
import com.github.xandergos.terraindiffusionmc.pipeline.PipelineModels;
import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipelineModelConfig;
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
        event.getDispatcher().register(literal("td-biome").executes(TerrainDiffusionMc::executeTdBiome));
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

    private static int executeTdBiome(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var pos = source.getPosition();
        int blockX = (int) Math.floor(pos.x);
        int blockZ = (int) Math.floor(pos.z);

        int scale = WorldScaleManager.getCurrentScale();
        float nativeRes = WorldPipelineModelConfig.nativeResolution();

        // Convert block coords to native pipeline pixel coords (i=Z, j=X convention).
        int pixX = Math.floorDiv(blockX, Math.max(1, scale));
        int pixZ = Math.floorDiv(blockZ, Math.max(1, scale));

        source.sendSuccess(() -> Component.literal(
                String.format("[TD] Querying %d, %d (native px %d, %d)…", blockX, blockZ, pixX, pixZ)),
                false);

        // Run on a daemon thread so the server thread is never blocked waiting for inference.
        Thread thread = new Thread(() -> {
            try {
                // 3×3 padded region: centre is the target pixel, neighbours supply the Sobel kernel.
                float[][] data = LocalTerrainProvider.getPipelineData(pixZ - 1, pixX - 1, pixZ + 2, pixX + 2, true);
                float[] elev3x3    = data[0];
                float[] climate3x3 = data[1];

                BiomeClassifier.DebugInfo info = BiomeClassifier.debugClassify(
                        pixX, pixZ, elev3x3, climate3x3, nativeRes);

                String msg = formatBiomeDebug(blockX, blockZ, pixX, pixZ, scale, info);
                source.getServer().execute(() -> source.sendSuccess(() -> Component.literal(msg), false));
            } catch (Exception e) {
                LOG.error("td-biome failed", e);
                source.getServer().execute(() ->
                        source.sendFailure(Component.literal("td-biome failed: " + e.getMessage())));
            }
        }, "td-biome-debug");
        thread.setDaemon(true);
        thread.start();

        return 1;
    }

    private static String formatBiomeDebug(int blockX, int blockZ, int pixX, int pixZ,
                                           int scale, BiomeClassifier.DebugInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[TD Biome] %d, %d", blockX, blockZ));
        if (scale > 1) sb.append(String.format(" (native px %d, %d  scale=%d)", pixX, pixZ, scale));
        sb.append(String.format("\n  temp=%.1f°C  precip=%.0f mm/yr  elev=%.0f m  slope=%.3f",
                info.temperature, info.precipitation, info.elevation, info.slope));
        if (info.matchingRegions.isEmpty()) {
            sb.append("\n  no matching region");
            if (info.fallbackRegion != null)
                sb.append(" → fallback: ").append(info.fallbackRegion);
        } else {
            sb.append("\n  regions: ").append(String.join(", ", info.matchingRegions));
        }
        return sb.toString();
    }
}
