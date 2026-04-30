package com.github.xandergos.terraindiffusionmc.hydro;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

/**
 * Applies hydrological features to a chunk that has just finished its surface pass.
 *
 * <p>For each block column in the chunk the placer :
 * <ol>
 *   <li>looks up the {@link HydrologicalFeature} in the matching {@link HydrologyTile}</li>
 *   <li>finds the surface Y from the chunk's {@link Heightmap.Type#WORLD_SURFACE_WG} index
 *       (the most reliable surface marker available at the noise stage)</li>
 *   <li>scans downward up to {@link FeatureBlockMapper#carveDepthFor} blocks ; if it ever
 *       sees lava, an open cave or any other protected block it aborts the column</li>
 *   <li>otherwise replaces the scanned blocks with the feature's block state</li>
 * </ol>
 *
 * <p>The placer is invoked from a {@code populateNoise} mixin on
 * {@code NoiseChunkGenerator} : at that point the heightmap is final but no features or
 * structures have been placed yet so the carving cannot accidentally erase decoration.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyChunkPlacer {

    private HydrologyChunkPlacer() {}

    /**
     * Places features on the given chunk. Reads the matching tile via
     * {@link HydrologyBuilder#getOrCompute} so the call is synchronous ; caller is expected
     * to be on the chunk worker thread.
     */
    public static void placeOnChunk(Chunk chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        // Block-space coordinates of the chunk's NW corner. Convention : i = Z (row), j = X (col).
        int chunkBlockI = chunkZ * 16;
        int chunkBlockJ = chunkX * 16;

        HydrologyTile tile = HydrologyBuilder.getOrCompute(chunkBlockI, chunkBlockJ);
        if (tile == null) return;

        Heightmap surfaceHm = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldI = chunkBlockI + localZ;
                int worldJ = chunkBlockJ + localX;
                int rowInTile = Math.floorMod(worldI, HydrologyBuilder.TILE_SIZE);
                int colInTile = Math.floorMod(worldJ, HydrologyBuilder.TILE_SIZE);

                int idx = rowInTile * tile.width + colInTile;
                HydrologicalFeature feature = HydrologicalFeature.fromOrdinal(tile.features[idx]);
                BlockState replacement = FeatureBlockMapper.blockStateFor(feature);
                if (replacement == null) continue;
                int depth = FeatureBlockMapper.carveDepthFor(feature);
                if (depth <= 0) continue;

                int surfaceY;
                if (feature == HydrologicalFeature.RIVER_MOUTH) {
                    // Mouth lives on the sea surface : carving from the floor up makes no sense.
                    // Replace exactly the cell at sea level + 1 (one block above the water).
                    surfaceY = HydrologyConstants.SEA_LEVEL_Y;
                } else {
                    surfaceY = surfaceHm.get(localX, localZ) - 1;
                }

                carveColumn(chunk, cursor, localX, localZ, surfaceY, depth, replacement);
            }
        }
    }

    /**
     * Walks down the column from {@code surfaceY} for {@code depth} blocks for replacing each cell
     * with {@code replacement}. Aborts on any cave / void / lava encounter : see
     * {@link FeatureBlockMapper#isCaveOrVoid} to avoid hovering rivers over open spaces.
     */
    private static void carveColumn(Chunk chunk, BlockPos.Mutable cursor,
                                    int localX, int localZ, int surfaceY,
                                    int depth, BlockState replacement) {
        // Pre-scan to make sure none of the blocks touch is a forbidden one.
        for (int i = 0; i < depth; i++) {
            int y = surfaceY - i;
            if (y < chunk.getBottomY()) return;
            cursor.set((chunk.getPos().x << 4) + localX, y, (chunk.getPos().z << 4) + localZ);
            BlockState existing = chunk.getBlockState(cursor);
            if (FeatureBlockMapper.isCaveOrVoid(existing)) return;
            if (FeatureBlockMapper.isProtected(existing)) return;
        }
        // Pre-scan passed : do the actual replacement.
        for (int i = 0; i < depth; i++) {
            int y = surfaceY - i;
            cursor.set((chunk.getPos().x << 4) + localX, y, (chunk.getPos().z << 4) + localZ);
            chunk.setBlockState(cursor, replacement, Block.NOTIFY_LISTENERS);
        }
    }
}