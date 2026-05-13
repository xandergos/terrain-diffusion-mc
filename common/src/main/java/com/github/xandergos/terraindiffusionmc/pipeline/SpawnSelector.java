package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.world.HeightConverter;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds a suitable land spawn point by querying the coarse elevation map near the world origin.
 *
 * <p>The search starts with a configurable NxN coarse-pixel region centered at (0, 0) and
 * expands by 8 coarse pixels per side each iteration until land is found or the max size is
 * reached. The first pixel (nearest to center) that is itself above sea level and fully
 * surrounded by 8 above-sea-level neighbors is chosen.
 */
public final class SpawnSelector {
    private static final Logger LOG = LoggerFactory.getLogger(SpawnSelector.class);

    /**
     * Coarse pixels to native pixels: 1 coarse unit = 32 * latentCompression native pixels.
     * Matches the conversion used in WorldPipeline#computeClimate.
     */
    private static final int COARSE_TO_NATIVE = 32 * WorldPipelineModelConfig.latentCompression();

    private SpawnSelector() {}

    /**
     * Finds the nearest fully-land coarse pixel to (0, 0) and converts it to a block-space
     * {@link BlockPos}. Falls back to (0, 64, 0) if no suitable pixel is found within the
     * configured maximum search region.
     */
    public static BlockPos findSpawnBlockPos() {
        int initialSize = TerrainDiffusionConfig.spawnSearchInitialSize();
        int maxSize = TerrainDiffusionConfig.spawnSearchMaxSize();
        int scale = WorldScaleManager.getCurrentScale();

        for (int regionSize = initialSize; regionSize <= maxSize; regionSize += 8) {
            int halfSize = regionSize / 2;
            // Coarse pixel bounds centered at native origin (coarse 0,0)
            int ci0 = -halfSize;
            int cj0 = -halfSize;
            int ci1 = halfSize;
            int cj1 = halfSize;

            LOG.info("Querying coarse map to find spawn. Region size: {} x {}", regionSize, regionSize);
            FloatTensor coarse;
            try {
                coarse = LocalTerrainProvider.getPipelineCoarse(ci0, cj0, ci1, cj1);
            } catch (Exception e) {
                LOG.error("SpawnSelector: failed to query coarse map at size {}", regionSize, e);
                continue;
            }

            int H = ci1 - ci0;
            int W = cj1 - cj0;

            BlockPos candidate = findNearestLandPixel(coarse, H, W, ci0, cj0, scale);
            if (candidate != null) {
                LOG.info("SpawnSelector: found land spawn at {} (coarse region {}x{})", candidate, regionSize, regionSize);
                return candidate;
            }

            LOG.debug("SpawnSelector: no land found in {}x{} coarse region, expanding", regionSize, regionSize);
        }

        LOG.warn("SpawnSelector: no land found within max coarse region {}x{}, falling back to origin", maxSize, maxSize);
        return fallbackToOrigin();
    }

    /**
     * Returns the Minecraft block Y for the given block (X, Z), clamped to at least 64.
     * Falls back to 64 if the heightmap cannot be fetched.
     */
    private static int heightmapY(int blockX, int blockZ) {
        try {
            LocalTerrainProvider.HeightmapData data =
                    LocalTerrainProvider.getInstance().fetchHeightmap(blockZ, blockX, blockZ + 1, blockX + 1);
            return Math.max(HeightConverter.convertToMinecraftHeight(data.heightmap[0][0]), 64);
        } catch (Exception e) {
            LOG.error("SpawnSelector: failed to fetch heightmap at ({}, {})", blockX, blockZ, e);
            return 64;
        }
    }

    /**
     * Returns a spawn position at block (0, 0) using the heightmap Y, clamped to at least 64.
     */
    private static BlockPos fallbackToOrigin() {
        LOG.warn("SpawnSelector: falling back to origin (0, ?, 0)");
        return new BlockPos(0, heightmapY(0, 0), 0);
    }

    /**
     * Scans the coarse tensor in order of increasing Chebyshev distance from the center,
     * returning the block position of the first pixel that is above sea level and fully
     * surrounded by 8 above-sea-level neighbors.
     *
     * @param coarse the coarse tensor with shape [7, H, W]
     * @param H      height in coarse pixels
     * @param W      width in coarse pixels
     * @param ci0    coarse row offset of the top-left corner
     * @param cj0    coarse col offset of the top-left corner
     * @param scale  current world scale (blocks per native pixel)
     * @return block-space position, or null if no valid pixel found
     */
    private static BlockPos findNearestLandPixel(FloatTensor coarse, int H, int W,
                                                  int ci0, int cj0, int scale) {
        // Center of the queried region in local (row, col) indices
        int centerRow = H / 2;
        int centerCol = W / 2;

        // Scan by increasing Chebyshev distance so the first hit is nearest to origin
        int maxDist = Math.max(H, W);
        for (int dist = 0; dist <= maxDist; dist++) {
            // Iterate over all pixels at this Chebyshev distance
            for (int dr = -dist; dr <= dist; dr++) {
                for (int dc = -dist; dc <= dist; dc++) {
                    // Only visit pixels on the outer shell of this distance
                    if (Math.abs(dr) != dist && Math.abs(dc) != dist) continue;

                    int row = centerRow + dr;
                    int col = centerCol + dc;

                    // Need a 1-pixel border for neighbor checks
                    if (row < 1 || row >= H - 1 || col < 1 || col >= W - 1) continue;

                    if (isFullyOnLand(coarse, row, col, H, W)) {
                        // Convert local coarse index to absolute coarse coords
                        int ci = ci0 + row;
                        int cj = cj0 + col;
                        // Coarse -> native -> block (j=X axis, i=Z axis)
                        int blockX = cj * COARSE_TO_NATIVE * scale;
                        int blockZ = ci * COARSE_TO_NATIVE * scale;
                        int blockY = heightmapY(blockX, blockZ);
                        return new BlockPos(blockX, blockY, blockZ);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns true if the pixel at (row, col) and all 8 of its neighbors have a positive
     * unnormalized elevation (i.e. are above sea level).
     */
    private static boolean isFullyOnLand(FloatTensor coarse, int row, int col, int H, int W) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (!isLand(coarse, row + dr, col + dc, H, W)) return false;
            }
        }
        return true;
    }

    /**
     * A coarse pixel is considered land when its unnormalized channel-0 value is positive.
     * Channel 6 is the blend weight; channel 0 / channel 6 gives the unnormalized elevation
     * (positive = land, zero or negative = ocean).
     */
    private static boolean isLand(FloatTensor coarse, int row, int col, int H, int W) {
        int pixelCount = H * W;
        int px = row * W + col;
        float weight = coarse.data[6 * pixelCount + px];
        if (weight <= 1e-6f) return false;
        float elevSqrt = coarse.data[px] / weight;  // channel 0
        return elevSqrt > 0f;
    }
}
