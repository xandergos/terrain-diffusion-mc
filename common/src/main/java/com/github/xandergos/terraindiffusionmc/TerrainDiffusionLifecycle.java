package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.explorer.ExplorerServer;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.platform.PlatformPaths;
import com.github.xandergos.terraindiffusionmc.pipeline.ModelAssetManager;
import com.github.xandergos.terraindiffusionmc.pipeline.PipelineModels;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionDensityFunction;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import com.mojang.serialization.MapCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;

/**
 * Loader-neutral lifecycle and command logic for terrain-diffusion-mc.
 */
public final class TerrainDiffusionLifecycle {
    public static final String MOD_ID = "terrain-diffusion-mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionLifecycle.class);
    public static final Identifier TERRAIN_DIFFUSION_ID = Identifier.fromNamespaceAndPath(MOD_ID, "terrain_diffusion");
    private static boolean initialized;

    private TerrainDiffusionLifecycle() {
    }

    /**
     * Loader entrypoints call this once with their platform-provided runtime paths.
     */
    public static synchronized void bootstrap(Path configDir, Path gameDir) {
        PlatformPaths.configure(configDir, gameDir);
        initialize();
    }

    /**
     * Runs common mod initialization once per loader.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
    }


    /**
     * Registers the common biome source codec through the active loader's registry hook.
     */
    public static void registerBiomeSourceCodecs(CodecRegistrar<MapCodec<? extends BiomeSource>> registrar) {
        registrar.register(TERRAIN_DIFFUSION_ID, TerrainDiffusionBiomeSource.CODEC);
    }

    /**
     * Registers the common density function codec through the active loader's registry hook.
     */
    public static void registerDensityFunctionCodecs(CodecRegistrar<MapCodec<? extends DensityFunction>> registrar) {
        registrar.register(TERRAIN_DIFFUSION_ID, TerrainDiffusionDensityFunction.CODEC);
    }

    @FunctionalInterface
    public interface CodecRegistrar<T> {
        void register(Identifier id, T value);
    }

    /**
     * Called by each loader when the server is starting.
     */
    public static void onServerStarting() {
        LocalTerrainProvider.clearCache();
    }

    /**
     * Called by each loader when a server level is loaded.
     */
    public static void onWorldLoad(ServerLevel world) {
        if (world.dimension() == Level.OVERWORLD) {
            WorldScaleManager.initializeForWorld(world);
            LocalTerrainProvider.init(world.getSeed());
        }
    }

    /**
     * Called by each loader when the server is stopping.
     */
    public static void onServerStopping() {
        ExplorerServer.stop();
    }

    /**
     * Registers common server commands on the loader-provided command dispatcher.
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("td-explore").executes(TerrainDiffusionLifecycle::executeExplore));
    }

    private static int executeExplore(CommandContext<CommandSourceStack> ctx) {
        try {
            int port = ExplorerServer.startIfNotRunning();
            String url = "http://localhost:" + port;
            MutableComponent link = Component.literal(url)
                    .withStyle(s -> s.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
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
