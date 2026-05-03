package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.RiverFluidPlacement;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "generateFeatures", at = @At("TAIL"))
    private void terrainDiffusion$placeRiverMaterialsAfterFeatures(
            StructureWorldAccess world,
            Chunk chunk,
            StructureAccessor structureAccessor,
            CallbackInfo ci
    ) {
        RiverFluidPlacement.onGenerateFeatures(world, chunk);
    }
}
