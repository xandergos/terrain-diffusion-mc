package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;

import net.minecraft.server.network.SpawnLocating;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldProperties.SpawnPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to pick an initial world spawn using the terrain models.
 *
 * This runs coarse sampling over a small grid then refines a single
 * candidate with a cheap local heightmap fetch.
 */
public final class SpawnSelector {

    private static final Logger LOG = LoggerFactory.getLogger(SpawnSelector.class);

    private static final int COARSE_GRID_SIZE = 10;     // number of samples along one axis
    private static final int COARSE_GRID_SPACING = 0;   // coarse tiles between samples
    private static final int TILE_GRID_SIZE = 4;        // number of chunk samples along on axis

    public static SpawnPoint findSpawnPoint(ServerWorld world) {
        TileSample coarseTile = calculateCoarseSpawnChunk();

        // If no valid spawn found, default to origin.
        if (!coarseTile.hasSample()) {
            LOG.warn("SpawnSelector: no valid coarse spawn found; defaulting to origin");
            return SpawnPoint.create(world.getRegistryKey(), BlockPos.ORIGIN, 0.0F, 0.0F);
        }

        // Refine the coarse tile to find a safe spawn position.
        BlockPos safeSpawnPos = findSafeSpawn(world, coarseTile);

        return SpawnPoint.create(world.getRegistryKey(), safeSpawnPos, 0.0F, 0.0F);
    }

    private static TileSample calculateCoarseSpawnChunk() {
        final int half = COARSE_GRID_SIZE / 2;
        TileSample tile = new TileSample();

        try {
            LOG.info("SpawnSelector: starting coarse sampling for Overworld");
            
            // Expand in square "rings" around (0, 0) in grid coordinates.
            // r is the ring radius in grid coordinates.
            for (int r = 0; r <= half; r++) {
                for (int x = -r; x <= r; x++) {
                    if (sampleTile(x, -r, tile)) { return tile; }
                    if (r != 0 && sampleTile(x, r, tile)) { return tile; }
                }
                if (r > 0) {
                    for (int z = -r + 1; z <= r - 1; z++) {
                        if (sampleTile(-r, z, tile)) { return tile; }
                        if (sampleTile(r, z, tile)) { return tile; }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("SpawnSelector: unexpected error", e);
        }

        // No valid spawn found; return an invalid sample.
        return tile;
    }

    /**
     * Sample a single point in the coarse grid.
     *
     * @param gridX grid coordinate in sample space (centred around 0)
     * @param gridZ grid coordinate in sample space (centred around 0)
     * @param best  mutable accumulator for the best sample found so far
     */
    private static boolean sampleTile(int gridX, int gridZ, TileSample tile) {
        int ci = gridX * (COARSE_GRID_SPACING + 1);
        int cj = gridZ * (COARSE_GRID_SPACING + 1);

        try {
            FloatTensor slice = LocalTerrainProvider.getPipelineCoarse(ci, cj, ci + 1, cj + 1);

            float w = slice.data[6];
            if (w <= 1e-8f) {
                return false;
            }

            // channel 0 = elev
            float raw = slice.data[0] / w;

            // Elevation scoring: sign(raw) * raw^2
            float elev = Math.copySign(raw * raw, raw);

            LOG.info("SpawnSelector: coarse sample at ({}, {}) has raw={} elev={} (w={})", ci, cj, raw, elev, w);

            // Pick first option above sea level
            if (elev > 0) {
                LOG.info("SpawnSelector: coarse sample found ({}, {}) elev={} (w={})", ci, cj, elev, w);
                tile.elev = elev;
                tile.ci = ci;
                tile.cj = cj;
                return true;
            }
        } catch (Exception e) {
            LOG.warn("SpawnSelector: coarse sample failed for ({}, {})", ci, cj, e);
        }

        return false;
    }

    /**
     * Mutable object for blocks
     */
    private static final class TileSample {
        float elev = Float.POSITIVE_INFINITY;
        int ci = 0;
        int cj = 0;

        boolean hasSample() {
            return elev != Float.POSITIVE_INFINITY;
        }
    }

    /**
     * Refine a coarse tile sample by checking multiple nearby chunks for a safe spawn point.
     *
     * @param world the server world to check for spawn safety
     * @param tile  the coarse tile sample to refine
     * @return a safe spawn position within the tile, or a fallback position if none found
     */
    private static BlockPos findSafeSpawn(ServerWorld world, TileSample tile) {
        // Convert coarse tile center to block coordinates.
        int scale = WorldScaleManager.getCurrentScale();
        int coarseTileBlockSize = 256 * scale;
        int chunkToTileScale = coarseTileBlockSize / 16; // number of chunks per coarse tile along on axis

        // Clamp the number of chunks to maximum possible to avoid resampling
        int numbChunks = Math.min(TILE_GRID_SIZE, chunkToTileScale);

        // Corner of the coarse tile in block coordinates
        int tileX = tile.cj * coarseTileBlockSize;
        int tileZ = tile.ci * coarseTileBlockSize;

        LOG.info("SpawnSelector: refining spawn around coarse tile at block ({}, {}) with scale {} ({} chunks per tile)", tileX, tileZ, scale, chunkToTileScale);

        for (int ti = 0; ti < numbChunks; ti++) {
            for (int tj = 0; tj < numbChunks; tj++) {
                // Calculate the block coordinates of the chunk to check
                int blockX = tileX + tj * chunkToTileScale * (16 / numbChunks);
                int blockZ = tileZ + ti * chunkToTileScale * (16 / numbChunks);
                LOG.info("SpawnSelector: checking chunk at ({}, {}) for safe spawn", blockX, blockZ);
                BlockPos pos = SpawnLocating.findServerSpawnPoint(world, new ChunkPos(blockX / 16, blockZ / 16));
                if (pos != null) {
                    LOG.info("SpawnSelector: refined spawn found at chunk ({}, {}) -> block ({}, {}, {})", blockX, blockZ, pos.getX(), pos.getY(), pos.getZ());
                    return pos;
                }
            }
        }

        return new BlockPos(tileX, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, tileX, tileZ), tileZ);
    }
}