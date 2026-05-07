package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Standalone diagnostic: sample the cave biome classifier over a 1024×1024 block
 * region at varying Y values and report the distribution of biome assignments.
 * If lush/dripstone never show up, the noise distribution doesn't reach the
 * thresholds and the dispatch path is fine — adjust thresholds. If they do show
 * up here, the bug is downstream (biome holder lookup, BiomeManager blend, etc).
 */
public final class CaveBiomeNoiseTest {

    private CaveBiomeNoiseTest() {}

    public static void main(String[] args) {
        int[] yLevels = { 50, 30, 20, 10, 0, -10, -30, -50 };
        int spanBlocks = 1024;
        int step = 4;

        for (int y : yLevels) {
            int total = 0, surface = 0, lush = 0, dripstone = 0, deepDark = 0;
            for (int x = 0; x < spanBlocks; x += step) {
                for (int z = 0; z < spanBlocks; z += step) {
                    short id = BiomeClassifier.classifyCaveBiome(x, y, z);
                    total++;
                    if      (id == -1)                                    surface++;
                    else if (id == BiomeClassifier.LUSH_CAVES)            lush++;
                    else if (id == BiomeClassifier.DRIPSTONE_CAVES)       dripstone++;
                    else if (id == BiomeClassifier.DEEP_DARK)             deepDark++;
                }
            }
            System.out.printf(
                    "Y=%4d  total=%d  surface=%5.1f%%  lush=%5.1f%%  drip=%5.1f%%  deep_dark=%5.1f%%%n",
                    y, total,
                    100.0 * surface / total,
                    100.0 * lush / total,
                    100.0 * dripstone / total,
                    100.0 * deepDark / total);
        }
    }
}
