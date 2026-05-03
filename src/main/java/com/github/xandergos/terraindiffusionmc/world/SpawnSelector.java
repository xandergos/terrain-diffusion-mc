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

    private static final int COARSE_GRID_SIZE = 10;   // number of samples along one axis
    private static final int COARSE_GRID_SPACING = 0; // coarse tiles between samples
    private static final int TILE_GRID_SIZE = 4;      // number of chunk samples along on axis
    private static final float MIN_VALID_WEIGHT = 1.0e-8f;
    private static final int MIN_SPAWN_ELEVATION = 10; // minimum elevation for valid spawn Coarse chunk (in meters)

    private SpawnSelector() {
    }

    public static SpawnPoint findSpawnPoint(ServerWorld world) {
        final CoarseSample coarseSample = calculateCoarseSpawnChunk();

        // If no valid spawn found, default to origin.
        if (coarseSample == null) {
            LOG.warn("SpawnSelector: no valid coarse spawn found; defaulting to origin");
            return SpawnPoint.create(world.getRegistryKey(), BlockPos.ORIGIN, 0.0F, 0.0F);
        }

        // Refine the coarse tile to find a safe spawn position.
        final BlockPos safeSpawnPos = findSafeSpawn(world, coarseSample);

        return SpawnPoint.create(world.getRegistryKey(), safeSpawnPos, 0.0F, 0.0F);
    }

    private static CoarseSample calculateCoarseSpawnChunk() {
        final int half = COARSE_GRID_SIZE / 2;
        final int sampleSpacing = COARSE_GRID_SPACING + 1;
        final int ciMin = -half * sampleSpacing;
        final int cjMin = -half * sampleSpacing;
        final int ciMaxExclusive = (half + 1) * sampleSpacing;
        final int cjMaxExclusive = (half + 1) * sampleSpacing;

        try {
            LOG.debug("SpawnSelector: starting coarse sampling for Overworld");
            final FloatTensor coarseSlice = LocalTerrainProvider.getPipelineCoarse(ciMin, cjMin, ciMaxExclusive, cjMaxExclusive);

            // Expand in square "rings" around (0, 0) in grid coordinates.
            // r is the ring radius in grid coordinates.
            for (int r = 0; r <= half; r++) {
                for (int x = -r; x <= r; x++) {
                    // Top edge
                    final CoarseSample topSample = sampleTile(coarseSlice, x, -r, sampleSpacing, ciMin, cjMin);
                    if (topSample != null) {
                        return topSample;
                    }
                    // Bottom edge
                    if (r != 0) {
                        final CoarseSample bottomSample = sampleTile(coarseSlice, x, r, sampleSpacing, ciMin, cjMin);
                        if (bottomSample != null) {
                            return bottomSample;
                        }
                    }
                }
                if (r > 0) {
                    for (int z = -r + 1; z <= r - 1; z++) {
                        // Left edge
                        final CoarseSample leftSample = sampleTile(coarseSlice, -r, z, sampleSpacing, ciMin, cjMin);
                        if (leftSample != null) {
                            return leftSample;
                        }
                        // Right edge
                        final CoarseSample rightSample = sampleTile(coarseSlice, r, z, sampleSpacing, ciMin, cjMin);
                        if (rightSample != null) {
                            return rightSample;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("SpawnSelector: unexpected error", e);
        }

        // No valid spawn found.
        return null;
    }

    /**
     * Sample a single point in the coarse grid.
     *
     * @param gridX grid coordinate in sample space (centred around 0)
     * @param gridZ grid coordinate in sample space (centred around 0)
     * @param sampleSpacing spacing between samples in grid coordinates
     * @param ciMin minimum grid coordinate in sample space (inclusive)
     * @param cjMin minimum grid coordinate in sample space (inclusive)
     * @return a coarse sample when this tile is valid, otherwise null
     */
        private static CoarseSample sampleTile(
            FloatTensor coarseSlice,
            int gridX,
            int gridZ,
            int sampleSpacing,
            int ciMin,
            int cjMin) {
        final int ci = gridX * sampleSpacing;
        final int cj = gridZ * sampleSpacing;

        final int sampleI = ci - ciMin;
        final int sampleJ = cj - cjMin;
        final int sliceHeight = coarseSlice.shape[1];
        final int sliceWidth = coarseSlice.shape[2];

        if (sampleI < 0 || sampleJ < 0 || sampleI >= sliceHeight || sampleJ >= sliceWidth) {
            LOG.warn("SpawnSelector: coarse sample index out of bounds for ({}, {})", ci, cj);
            return null;
        }

        final int pixel = sampleI * sliceWidth + sampleJ;
        final int planeSize = sliceHeight * sliceWidth;
        final float weight = coarseSlice.data[6 * planeSize + pixel];
        if (weight <= MIN_VALID_WEIGHT) {
            return null;
        }

        // channel 0 = elev
        final float raw = coarseSlice.data[pixel] / weight;

        // Elevation scoring: sign(raw) * raw^2
        final float elev = Math.copySign(raw * raw, raw);

        LOG.debug("SpawnSelector: coarse sample at ({}, {}) has raw={} elev={} (w={})", ci, cj, raw, elev, weight);

        // Pick first option above sea level
        if (elev > MIN_SPAWN_ELEVATION) {
            LOG.debug("SpawnSelector: coarse sample found ({}, {}) elev={} (w={})", ci, cj, elev, weight);
            return new CoarseSample(ci, cj);
        }

        return null;
    }

    private record CoarseSample(int ci, int cj) {
    }

    /**
     * Refine a coarse tile sample by checking multiple nearby chunks for a safe spawn point.
     *
     * @param world the server world to check for spawn safety
     * @param coarseSample the coarse tile sample to refine
     * @return a safe spawn position within the tile, or a fallback position if none found
     */
    private static BlockPos findSafeSpawn(ServerWorld world, CoarseSample coarseSample) {
        // Convert coarse tile center to block coordinates.
        final int scale = WorldScaleManager.getCurrentScale();
        final int coarseTileBlockSize = 256 * scale;
        final int chunksPerTile = coarseTileBlockSize / 16; // number of chunks per coarse tile along on axis

        // Clamp the number of chunks to maximum possible to avoid resampling
        final int chunksToCheck = Math.min(TILE_GRID_SIZE, chunksPerTile);

        // Corner of the coarse tile in block coordinates
        final int tileX = coarseSample.cj() * coarseTileBlockSize;
        final int tileZ = coarseSample.ci() * coarseTileBlockSize;
        final int tileChunkX = tileX / 16;
        final int tileChunkZ = tileZ / 16;
        final int chunkStride = chunksPerTile / chunksToCheck;

        LOG.debug("SpawnSelector: refining spawn around coarse tile at block ({}, {}) with scale {} ({} chunks per tile)", tileX, tileZ, scale, chunksPerTile);

        for (int offsetZ = 0; offsetZ < chunksToCheck; offsetZ++) {
            for (int offsetX = 0; offsetX < chunksToCheck; offsetX++) {
                // Calculate the chunk coordinates to check.
                final ChunkPos chunkPos = new ChunkPos(
                        tileChunkX + offsetX * chunkStride,
                        tileChunkZ + offsetZ * chunkStride);
                LOG.debug("SpawnSelector: checking chunk at ({}, {}) for safe spawn", chunkPos.x, chunkPos.z);
                final BlockPos pos = SpawnLocating.findServerSpawnPoint(world, chunkPos);
                if (pos != null) {
                    LOG.debug("SpawnSelector: refined spawn found at chunk ({}, {}) -> block ({}, {}, {})", chunkPos.x, chunkPos.z, pos.getX(), pos.getY(), pos.getZ());
                    return pos;
                }
            }
        }

        return new BlockPos(tileX, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, tileX, tileZ), tileZ);
    }
}