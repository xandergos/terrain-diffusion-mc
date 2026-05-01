package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.SpawnSelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.SpawnLocating;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldProperties.SpawnPoint;
import net.minecraft.world.chunk.ChunkLoadProgress;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "setupSpawn", at = @At("HEAD"), cancellable = true)
    private static void overrideWorldSpawn(ServerWorld world, ServerWorldProperties worldProperties, boolean bonusChest, boolean debugWorld, ChunkLoadProgress loadProgress, CallbackInfo ci) {
        // Ensure this only runs for the Overworld
        if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) {
            return;
        }

        // Calculate your target coordinates
        // This method must block until the terrain model returns the desired X/Z.
        ChunkPos targetChunk = SpawnSelector.calculateCoarseSpawnChunk(world);

        int targetX = targetChunk.getCenterX();
        int targetZ = targetChunk.getCenterZ();

        // Use Vanilla logic to find a safe block within that area
        BlockPos safeSpawnPos = SpawnLocating.findServerSpawnPoint(world, new ChunkPos(targetChunk.x, targetChunk.z));

        // Fallback to middle of the block if finding a saf spawn position fails.
        if (safeSpawnPos == null) {
            int spawnY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
            safeSpawnPos = new BlockPos(targetX, spawnY, targetZ);
        }

        worldProperties.setSpawnPoint(SpawnPoint.create(world.getRegistryKey(), safeSpawnPos, 0.0F, 0.0F));
        ci.cancel();
    }
}