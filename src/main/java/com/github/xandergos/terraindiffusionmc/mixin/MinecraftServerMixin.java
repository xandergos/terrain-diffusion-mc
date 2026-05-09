package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.pipeline.SpawnSelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkLoadProgress;
import net.minecraft.world.level.ServerWorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "setupSpawn", at = @At("HEAD"), cancellable = true)
    private static void overrideWorldSpawn(ServerWorld world, ServerWorldProperties worldProperties,
                                           boolean bonusChest, boolean debugWorld,
                                           ChunkLoadProgress loadProgress, CallbackInfo ci) {
        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            return;
        }

        BlockPos spawnPos = SpawnSelector.findSpawnBlockPos();
        worldProperties.setSpawnPoint(
                WorldProperties.SpawnPoint.create(World.OVERWORLD, spawnPos, 0f, 0f));
        ci.cancel();
    }
}
