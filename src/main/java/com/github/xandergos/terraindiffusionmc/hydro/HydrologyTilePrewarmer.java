package com.github.xandergos.terraindiffusionmc.hydro;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/**
 * Server-side service that maintains a rolling buffer of pre-computed {@link HydrologyTile}
 * entries around every online player.
 *
 * <p>Each tick walk the player list, compute their current tile and ask the builder to
 * (re-)schedule every tile in a {@code (2 * RADIUS + 1) ^ 2} window centred on it. The builder
 * deduplicates already-cached or pending tiles so most tick calls are no-ops ; only when the
 * player crosses a tile boundary do new builds get scheduled.
 *
 * <p>Why pre-warm : the chunk-gen worldgen path uses
 * {@link HydrologyBuilder#getOrCompute} which is synchronous. If the matching tile is already
 * in cache (because the pre-warmer scheduled it earlier and the executor finished it),
 * {@code getOrCompute} returns instantly. If not it pays the full ~25 ms compute cost on the
 * chunk thread which is visible as a brief stutter when terrain generates faster than the
 * pre-warmer can keep up.
 *
 * <p>Tick budget : don't gate this on tick count or distance. {@link HydrologyBuilder}
 * already deduplicates pending work so calling it every tick is cheap.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyTilePrewarmer {

    /** Half-extent of the rolling buffer in tiles. {@code 2} produces a 5x5 = 25-tile window. */
    public static final int RADIUS = 2;

    private HydrologyTilePrewarmer() {}

    /** Registers the per-tick listener. Call from the main mod entry point. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HydrologyTilePrewarmer::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            for (PlayerEntity player : world.getPlayers()) {
                int playerBlockI = (int) Math.floor(player.getZ());
                int playerBlockJ = (int) Math.floor(player.getX());
                int playerTileI = Math.floorDiv(playerBlockI, HydrologyBuilder.TILE_SIZE);
                int playerTileJ = Math.floorDiv(playerBlockJ, HydrologyBuilder.TILE_SIZE);

                for (int dI = -RADIUS; dI <= RADIUS; dI++) {
                    for (int dJ = -RADIUS; dJ <= RADIUS; dJ++) {
                        int tileBlockI = (playerTileI + dI) * HydrologyBuilder.TILE_SIZE;
                        int tileBlockJ = (playerTileJ + dJ) * HydrologyBuilder.TILE_SIZE;
                        // getOrSchedule returns null if not yet built ; the side effect is the
                        // important part. It kicks off the async build if missing and pending.
                        HydrologyBuilder.getOrSchedule(tileBlockI, tileBlockJ);
                    }
                }
            }
        }
    }
}