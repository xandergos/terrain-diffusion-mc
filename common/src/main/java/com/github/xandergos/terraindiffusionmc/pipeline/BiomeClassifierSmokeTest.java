package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.biome.BiomePalette;

import java.util.Map;
import java.util.TreeMap;

/**
 * Lightweight classifier-only smoke test. It does not load ONNX models or modded biome registries.
 *
 * <p>Run from the common source set before compatibility-mod work to verify that the vanilla
 * classifier emits a reasonable spread of IDs on a simple synthetic world.</p>
 */
public final class BiomeClassifierSmokeTest {
    private BiomeClassifierSmokeTest() {}

    public static void main(String[] args) {
        int H = 64;
        int W = 64;
        float[] elev = new float[H * W];
        float[] climate = new float[4 * H * W];
        float[] padded = new float[(H + 2) * (W + 2)];

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;

                // Four simple climate zones plus a narrow mountain ridge. This intentionally uses
                // only the four pipeline climate values consumed by BiomeClassifier.
                if (r < H / 2 && c < W / 2) {
                    // Cold/wet: snowy plains, taiga variants, frozen ocean if below sea level.
                    elev[idx] = 80f;
                    setClimate(climate, H, W, idx, -7f, 2200f, 520f, 45f);
                } else if (r < H / 2) {
                    // Hot/dry highland: desert/badlands family.
                    elev[idx] = 650f;
                    setClimate(climate, H, W, idx, 32f, 900f, 180f, 70f);
                } else if (c < W / 2) {
                    // Temperate/wet upland: forest, flower/birch/dark forest variants.
                    elev[idx] = 420f;
                    setClimate(climate, H, W, idx, 16f, 700f, 950f, 30f);
                } else {
                    // Warm/wet lowland with a submerged patch: swamp/jungle/ocean variants.
                    elev[idx] = (r > 48 && c > 48) ? -260f : 60f;
                    setClimate(climate, H, W, idx, 25f, 600f, 1450f, 35f);
                }

                if (Math.abs(c - W / 2) < 2) {
                    elev[idx] += 1800f + r * 25f;
                }
            }
        }

        for (int r = 0; r < H + 2; r++) {
            for (int c = 0; c < W + 2; c++) {
                int rr = Math.max(0, Math.min(H - 1, r - 1));
                int cc = Math.max(0, Math.min(W - 1, c - 1));
                padded[r * (W + 2) + c] = elev[rr * W + cc];
            }
        }

        short[] biomes = BiomeClassifier.classify(elev, climate, 0, 0, padded, H, W, 4f);
        Map<Short, Integer> counts = new TreeMap<>();
        for (short biome : biomes) counts.merge(biome, 1, Integer::sum);

        System.out.println("BiomeClassifier smoke-test counts:");
        for (Map.Entry<Short, Integer> entry : counts.entrySet()) {
            System.out.printf("%3d %-28s %d%n", entry.getKey(), BiomePalette.nameOf(entry.getKey()), entry.getValue());
        }

        if (counts.size() < 6) {
            throw new IllegalStateException("Expected at least 6 biome classes, got " + counts.size());
        }
        if (!counts.containsKey(BiomePalette.STONY_PEAKS)) {
            throw new IllegalStateException("Expected mountain ridge to keep emitting stony_peaks");
        }
    }

    private static void setClimate(float[] climate, int H, int W, int idx,
                                   float temp, float tSeason, float precip, float pCV) {
        int area = H * W;
        climate[idx] = temp;
        climate[area + idx] = tSeason;
        climate[2 * area + idx] = precip;
        climate[3 * area + idx] = pCV;
    }
}
