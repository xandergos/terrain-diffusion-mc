package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.hydro.RiverGridCache;
import com.github.xandergos.terraindiffusionmc.hydro.RiverNetwork;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After surface generation fills carved river beds with water.
 * Targets the concrete NoiseChunkGenerator.buildSurface overload which
 * delegates to the internal surface builder : it has a proper RETURN.
 */
@Mixin(NoiseChunkGenerator.class)
public abstract class RiverWaterPlacementMixin {

    private static final BlockState WATER = Blocks.WATER.getDefaultState();

    @Inject(
            method = "buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",
            at = @At("TAIL")
    )
    private void terrainDiffusionMc$placeRiverWater(
            ChunkRegion region,
            StructureAccessor structures,
            NoiseConfig noiseConfig,
            Chunk chunk,
            CallbackInfo ci) {

        ChunkPos chunkPos = chunk.getPos();
        RiverNetwork.ChunkMaps maps = RiverGridCache.getMapsForChunk(chunkPos.x, chunkPos.z);
        boolean[] waterMap = maps.water();

        // Quick exit — no river cells in this chunk
        boolean anyWater = false;
        for (boolean b : waterMap) { if (b) { anyWater = true; break; } }
        if (!anyWater) return;

        int originX = chunkPos.getStartX();
        int originZ = chunkPos.getStartZ();
        int minY    = chunk.getBottomY();
        // countVerticalSections() * 16 + bottomY = topY
        int scanTopY = minY + chunk.countVerticalSections() * 16 - 1;

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                if (!waterMap[lz * 16 + lx]) continue;

                int wx = originX + lx;
                int wz = originZ + lz;

                // Find the topmost solid (non-air, non-water) block
                int solidY = scanTopY;
                while (solidY > minY) {
                    BlockState bs = chunk.getBlockState(mutable.set(wx, solidY, wz));
                    if (!bs.isAir() && !bs.isOf(Blocks.WATER)) break;
                    solidY--;
                }
                // solidY is the carved river bed surface

                // Fill 3 air blocks above it with water
                for (int y = solidY + 1; y <= solidY + 3 && y <= scanTopY; y++) {
                    mutable.set(wx, y, wz);
                    if (chunk.getBlockState(mutable).isAir()) {
                        chunk.setBlockState(mutable, WATER, 0);
                    } else {
                        break;
                    }
                }
            }
        }
    }
}