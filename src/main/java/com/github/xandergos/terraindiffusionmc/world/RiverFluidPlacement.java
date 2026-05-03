package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Non-blocking post-generation water placement for carved river beds.
 *
 * <p>Important : this class never calls LocalTerrainProvider.fetchHeightmap().
 * The density function already forces the relevant tile to be generated during
 * terrain generation ; this pass only consumes a ready cached tile. If the tile is
 * not ready for any reason the chunk is skipped instead of freezing generation.</p>
 */
public final class RiverFluidPlacement {
    private static final short RIVER_BIOME_ID = 7;
    private static final int CHUNK_SIZE = 16;

    private RiverFluidPlacement() {
    }

    public static void onChunkGenerate(ServerWorld world, WorldChunk chunk) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int chunkStartX = chunkPos.getStartX();
        int chunkStartZ = chunkPos.getStartZ();

        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = chunkStartX >> tileShift;
        int tileZ = chunkStartZ >> tileShift;

        int tileStartX = tileX << tileShift;
        int tileStartZ = tileZ << tileShift;
        int tileEndX = tileStartX + tileSize;
        int tileEndZ = tileStartZ + tileSize;

        HeightmapData data = LocalTerrainProvider.getReadyHeightmap(tileStartZ, tileStartX, tileEndZ, tileEndX);
        if (data == null || data.heightmap == null || data.biomeIds == null) {
            return;
        }

        int minY = world.getBottomY();
        int maxY = world.getBottomY() + world.getHeight() - 1;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int dz = 0; dz < CHUNK_SIZE; dz++) {
            int worldZ = chunkStartZ + dz;
            int localZ = worldZ - tileStartZ;
            if (localZ < 0 || localZ >= data.height) continue;

            for (int dx = 0; dx < CHUNK_SIZE; dx++) {
                int worldX = chunkStartX + dx;
                int localX = worldX - tileStartX;
                if (localX < 0 || localX >= data.width) continue;
                if (data.biomeIds[localZ][localX] != RIVER_BIOME_ID) continue;

                int bedY = HeightConverter.convertToMinecraftHeight(data.heightmap[localZ][localX]);
                int depth = estimateWaterDepth(data, localZ, localX);
                int waterBottomY = Math.max(minY, bedY + 1);
                int waterTopY = Math.min(maxY, bedY + depth);
                if (waterBottomY > waterTopY) continue;

                for (int y = waterBottomY; y <= waterTopY; y++) {
                    pos.set(worldX, y, worldZ);
                    BlockState current = chunk.getBlockState(pos);
                    if (current.isAir() || current.isOf(Blocks.WATER)) {
                        chunk.setBlockState(pos, Blocks.WATER.getDefaultState(), 0);
                    }
                }
            }
        }
    }

    private static int estimateWaterDepth(HeightmapData data, int z, int x) {
        int eastWest = 1 + countRiverRun(data, z, x, 0, 1) + countRiverRun(data, z, x, 0, -1);
        int northSouth = 1 + countRiverRun(data, z, x, 1, 0) + countRiverRun(data, z, x, -1, 0);
        int diagonalA = 1 + countRiverRun(data, z, x, 1, 1) + countRiverRun(data, z, x, -1, -1);
        int diagonalB = 1 + countRiverRun(data, z, x, 1, -1) + countRiverRun(data, z, x, -1, 1);
        int width = Math.max(Math.max(eastWest, northSouth), Math.max(diagonalA, diagonalB));

        if (width >= 11) return 4;
        if (width >= 7) return 3;
        if (width >= 4) return 2;
        return 1;
    }

    private static int countRiverRun(HeightmapData data, int z, int x, int dz, int dx) {
        int count = 0;
        for (int step = 1; step <= 8; step++) {
            int zz = z + dz * step;
            int xx = x + dx * step;
            if (zz < 0 || xx < 0 || zz >= data.height || xx >= data.width) break;
            if (data.biomeIds[zz][xx] != RIVER_BIOME_ID) break;
            count++;
        }
        return count;
    }
}
