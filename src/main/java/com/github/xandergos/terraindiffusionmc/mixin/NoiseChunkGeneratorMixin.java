package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.hydro.HydrologyChunkPlacer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Hooks {@link ChunkGenerator#populateNoise} on the base class so every concrete chunk
 * generator (vanilla noise, flat, debug, modded ones extending ChunkGenerator) gets the
 * hydrology placement. We attach a {@code thenApply} continuation to the returned future so
 * the placer runs once the noise pass completes ; running on the same thread that produced
 * the chunk avoids cross-thread chunk mutation.
 *
 * <p>Vanilla pipeline order is noise -> surface -> carvers -> features -> structures. Placing
 * features here means our river blocks may sit underneath grass blocks added by the surface
 * pass. If a future iteration shows surface rules overwriting placed river cells this hook
 * can be moved to a {@code buildSurface} mixin instead.
 *
 * <p>Note for 1.21.4+ : the {@code populateNoise} signature lost its {@code Executor} parameter
 * compared to earlier versions and {@link NoiseConfig} now lives in
 * {@code net.minecraft.world.gen.noise} rather than {@code net.minecraft.world.gen.chunk}.
 */
@Mixin(NoiseChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin {

    @Inject(
            method = "populateNoise",
            at = @At("RETURN"),
            cancellable = true
    )
    private void terrainDiffusionMc$placeHydrology(
            Blender blender,
            NoiseConfig noiseConfig,
            StructureAccessor structureAccessor,
            Chunk chunk,
            CallbackInfoReturnable<CompletableFuture<Chunk>> cir
    ) {
        CompletableFuture<Chunk> original = cir.getReturnValue();
        if (original == null) return;

        cir.setReturnValue(original.thenApply(populated -> {
            try {
                HydrologyChunkPlacer.placeOnChunk(populated);
            } catch (Throwable t) {
                System.err.println("[terrain-diffusion-mc] hydrology placement failed: " + t);
                t.printStackTrace();
            }
            return populated;
        }));
    }
}