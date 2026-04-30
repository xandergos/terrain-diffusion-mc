package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Utility to pick an initial world spawn using the terrain models.
 *
 * This runs coarse sampling over a small grid then refines a single
 * candidate with a cheap local heightmap fetch.
 */
public final class SpawnSelector {
    private static final Logger LOG = LoggerFactory.getLogger(SpawnSelector.class);

    public static ChunkPos calculateCoarseSpawnChunk() {
        try {
            LOG.info("SpawnSelector: starting coarse sampling for Overworld");

            // Grid parameters: 10x10 sampling in coarse tiles centred at 0
            final int GRID = 10;
            final int SPACING = 2;
            final int HALF = GRID / 2;

            float bestElev = Float.NEGATIVE_INFINITY;
            int bestCi = 0, bestCj = 0;

            // TODO: This currently just selects the highest elevation...
            for (int gi = -HALF; gi < HALF; gi++) {
                for (int gj = -HALF; gj < HALF; gj++) {
                    int ci = gi * SPACING;
                    int cj = gj * SPACING;
                    try {
                        FloatTensor slice = LocalTerrainProvider.getPipelineCoarse(ci, cj, ci + 1, cj + 1);
                        // slice shape: [7, H, W] with H=W=1
                        float w = slice.data[6];
                        float raw = (w > 1e-8f) ? slice.data[0] / w : 0f; // channel 0 = elev
                        float elev = (float) (Math.signum(raw) * raw * raw);
                        if (elev > bestElev) {
                            bestElev = elev;
                            bestCi = ci; bestCj = cj;
                        }
                    } catch (Exception e) {
                        LOG.warn("SpawnSelector: coarse sample failed for ({},{})", ci, cj, e);
                    }
                }
            }

            LOG.info("SpawnSelector: best coarse tile ({}, {}) elev={} — refining...", bestCi, bestCj, bestElev);

            // Convert coarse tile center to block coordinates.
            final int COARSE_TILE_BLOCK = 256;
            int centerBlockX = bestCj * COARSE_TILE_BLOCK + COARSE_TILE_BLOCK / 2;
            int centerBlockZ = bestCi * COARSE_TILE_BLOCK + COARSE_TILE_BLOCK / 2;

            return new ChunkPos(new BlockPos(centerBlockX, 0, centerBlockZ));
        } catch (Exception e) {
            LOG.error("SpawnSelector: unexpected error", e);
            return new ChunkPos(new BlockPos(0, 100, 0)); // TODO: This should also use sea level as a default
        }
    }
}
