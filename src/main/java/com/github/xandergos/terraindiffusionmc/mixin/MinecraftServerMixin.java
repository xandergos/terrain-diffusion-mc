package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.SpawnSelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.WorldProperties.SpawnPoint;
import net.minecraft.world.chunk.ChunkLoadProgress;
import net.minecraft.world.level.ServerWorldProperties;
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

        SpawnPoint spawnPoint = SpawnSelector.findSpawnPoint(world);

        worldProperties.setSpawnPoint(spawnPoint);
        ci.cancel();
    }
}