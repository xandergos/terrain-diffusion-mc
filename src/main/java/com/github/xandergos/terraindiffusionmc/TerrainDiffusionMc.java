package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.block.ModBlocks;
import com.github.xandergos.terraindiffusionmc.explorer.ExplorerServer;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.ModelAssetManager;
import com.github.xandergos.terraindiffusionmc.pipeline.PipelineModels;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionDensityFunction;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import java.net.URI;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public class TerrainDiffusionMc implements ModInitializer {
    public static final String MOD_ID = "terrain-diffusion-mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    @Override
    public void onInitialize() {
        LOG.info("Initializing terrain-diffusion-mc");
        Registry.register(Registries.BIOME_SOURCE, Identifier.of(MOD_ID, "terrain_diffusion"), TerrainDiffusionBiomeSource.CODEC);
        Registry.register(Registries.DENSITY_FUNCTION_TYPE, Identifier.of(MOD_ID, "terrain_diffusion"), TerrainDiffusionDensityFunction.CODEC);

        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();

        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                WorldScaleManager.initializeForWorld(world);
                LocalTerrainProvider.init(world.getSeed());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ExplorerServer.stop());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("td-explore").executes(TerrainDiffusionMc::executeExplore))
        );

        // layers nature blocks
        ModBlocks.register();
    }

    private static int executeExplore(CommandContext<ServerCommandSource> ctx) {
        try {
            int port = ExplorerServer.startIfNotRunning();
            String url = "http://localhost:" + port;
            MutableText link = Text.literal(url)
                    .styled(s -> s.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                            .withUnderline(true));
            ctx.getSource().sendFeedback(
                    () -> Text.literal("Terrain Explorer: ").append(link),
                    false);
        } catch (Exception e) {
            LOG.error("Failed to start terrain explorer", e);
            ctx.getSource().sendError(Text.literal("Failed to start terrain explorer: " + e.getMessage()));
        }
        return 1;
    }
}