package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.server.world.ServerWorld;
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

    // Coarse sampling grid configuration (in coarse-tile coordinates)
    private static final int GRID_SIZE = 10;    // number of samples along one axis
    private static final int SPACING = 0;       // coarse tiles between samples

    private SpawnSelector() {
        // Utility class; no instances
    }

    public static ChunkPos calculateCoarseSpawnChunk() {

        try {
            LOG.info("SpawnSelector: starting coarse sampling for Overworld");

            final int half = GRID_SIZE / 2;
            BlockSample block = new BlockSample();

            // Expand in square "rings" around (0, 0) in grid coordinates.
            // r is the ring radius in grid coordinates.
            for (int r = 0; r <= half; r++) {
                for (int x = -r; x <= r; x++) {
                    if (samplePoint(x, -r, block) || (r != 0 && samplePoint(x, r, block))) {
                        return coarseToChunk(block);
                    }
                }
                if (r > 0) {
                    for (int z = -r + 1; z <= r - 1; z++) {
                        if (samplePoint(-r, z, block) || samplePoint(r, z, block)) {
                            return coarseToChunk(block);
                        }
                    }
                }
            }

            if (!block.hasSample()) {
                LOG.warn("SpawnSelector: no valid coarse samples found; falling back to origin");
                return new ChunkPos(new BlockPos(0, 0, 0));
            }

            LOG.info("SpawnSelector: best coarse tile ({}, {}) elev={} — refining...",
                     block.ci, block.cj, block.elev);

            // Convert coarse tile center to block coordinates.
            // NOTE: ci/cj are in coarse-tile coordinates; mapping to X/Z depends on scale.
            int scale = WorldScaleManager.getCurrentScale();
            final int coarseTileBlockSize = 256 * scale;

            int centerBlockX = block.ci * coarseTileBlockSize + coarseTileBlockSize / 2;
            int centerBlockZ = block.cj * coarseTileBlockSize + coarseTileBlockSize / 2;

            return new ChunkPos(new BlockPos(centerBlockX, 0, centerBlockZ));
        } catch (Exception e) {
            LOG.error("SpawnSelector: unexpected error", e);
            return new ChunkPos(new BlockPos(0, 0, 0));
        }
    }

    /**
     * Convert block coordinates to chunk coordinates
     *
     * @param block Coarse block sample object
     * @return Corner chunk position of course block
     */
    private static ChunkPos coarseToChunk(BlockSample block) {
        int scale = WorldScaleManager.getCurrentScale();
        return new ChunkPos(block.cj * 256 * scale >> 4, block.ci * 256 * scale >> 4);
    }

    /**
     * Sample a single point in the coarse grid.
     *
     * @param gridX grid coordinate in sample space (centred around 0)
     * @param gridZ grid coordinate in sample space (centred around 0)
     * @param best  mutable accumulator for the best sample found so far
     */
    private static boolean samplePoint(int gridX, int gridZ, BlockSample block) {
        int ci = gridX * SPACING;
        int cj = gridZ * SPACING;

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

            // Pick first option above sea level
            if (elev > 0) {
                block.elev = elev;
                block.ci = ci;
                block.cj = cj;
            }
            return true;
        } catch (Exception e) {
            LOG.warn("SpawnSelector: coarse sample failed for ({}, {})", ci, cj, e);
            return false;
        }
    }

    /**
     * Mutable object for blocks
     */
    private static final class BlockSample {
        float elev = Float.POSITIVE_INFINITY;
        int ci;
        int cj;

        boolean hasSample() {
            return elev != Float.POSITIVE_INFINITY;
        }
    }
}