package com.github.xandergos.terraindiffusionmc.pipeline;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Objects;

/**
 * Generates synthetic climate conditioning maps from Perlin noise quantile-matched
 * to real WorldClim/ETOPO distributions, matching world_pipeline.py make_synthetic_map_factory.
 *
 * <p>Uses precomputed quantile tables from pipeline_data.json (generated once from WorldClim data).
 * Per-world variation comes from the worldSeed used to initialize each channel's FastNoiseLite.
 */
public final class SyntheticMapFactory {

    private static final int N_CHANNELS = 5;
    private static final float[] FREQUENCY_MULT = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
    private static final float BASE_FREQUENCY = 0.05f;
    private static final int[] OCTAVES = {4, 2, 4, 4, 4};
    private static final float LACUNARITY = 2.0f;
    private static final float GAIN = 0.5f;

    // Loaded from pipeline_data.json (data quantiles are seed-independent WorldClim distributions)
    private final float[][] noiseQuantiles;
    private final float[][] dataQuantiles;
    private final float aTempStd;
    private final float bTempStd;
    private final float tempStdP1;
    private final float tempStdP99;

    // Per-seed noise instances (channels 0..4)
    private final FastNoiseLite[] noises = new FastNoiseLite[N_CHANNELS];

    private static float[][] cachedDataQuantiles;
    private static float cachedATempStd;
    private static float cachedBTempStd;
    private static float cachedTempStdP1;
    private static float cachedTempStdP99;
    private static boolean dataLoaded = false;

    /** @param worldSeed 64-bit world seed (Python: seed & 0xFFFFFFFFFFFFFFFF). Per-channel seeds use lower 32 bits. */
    public SyntheticMapFactory(long worldSeed) {
        loadDataIfNeeded();
        this.dataQuantiles = cachedDataQuantiles;
        this.aTempStd = cachedATempStd;
        this.bTempStd = cachedBTempStd;
        this.tempStdP1 = cachedTempStdP1;
        this.tempStdP99 = cachedTempStdP99;

        this.noiseQuantiles = new float[N_CHANNELS][];
        for (int ch = 0; ch < N_CHANNELS; ch++) {
            FastNoiseLite fnl = new FastNoiseLite((int) ((worldSeed + ch + 1) & 0x7FFFFFFFL));
            fnl.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
            fnl.SetFrequency(BASE_FREQUENCY * FREQUENCY_MULT[ch]);
            fnl.SetFractalType(FastNoiseLite.FractalType.FBm);
            fnl.SetFractalOctaves(OCTAVES[ch]);
            fnl.SetFractalLacunarity(LACUNARITY);
            fnl.SetFractalGain(GAIN);
            noises[ch] = fnl;
            this.noiseQuantiles[ch] = buildNoiseQuantiles(fnl, 64, 1e-4f);
        }
    }

    private static synchronized void loadDataIfNeeded() {
        if (dataLoaded) return;
        try (InputStream is = SyntheticMapFactory.class.getResourceAsStream("/pipeline_data.json");
             InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(is))) {
            JsonObject data = new Gson().fromJson(reader, JsonObject.class);
            int nQ = data.get("n_quantiles").getAsInt();
            JsonArray dataArr = data.getAsJsonArray("data_quantile_tables");
            cachedDataQuantiles = new float[N_CHANNELS][nQ];
            for (int ch = 0; ch < N_CHANNELS; ch++) {
                JsonArray dq = dataArr.get(ch).getAsJsonArray();
                for (int i = 0; i < nQ; i++) {
                    cachedDataQuantiles[ch][i] = dq.get(i).getAsFloat();
                }
            }
            cachedATempStd = data.get("a_temp_std").getAsFloat();
            cachedBTempStd = data.get("b_temp_std").getAsFloat();
            cachedTempStdP1 = data.get("temp_std_p1").getAsFloat();
            cachedTempStdP99 = data.get("temp_std_p99").getAsFloat();
            dataLoaded = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load pipeline_data.json", e);
        }
    }

    /**
     * Compute noise quantile table for a FastNoiseLite instance.
     * Samples on a 1024x1024 grid over [0, 32768) at stride 32, matching Python's
     * _compute_map_stats: x/y in arange(0, 32*1024, 32).
     */
    static float[] buildNoiseQuantiles(FastNoiseLite fnl, int nQuantiles, float eps) {
        float[] values = new float[1024 * 1024];
        int k = 0;
        for (int r = 0; r < 1024; r++)
            for (int c = 0; c < 1024; c++)
                values[k++] = fnl.GetNoise(c * 32, r * 32);
        Arrays.sort(values);

        float[] q = new float[nQuantiles];
        int n = values.length;
        for (int i = 0; i < nQuantiles; i++) {
            float pct = eps + i * (1.0f - 2 * eps) / (nQuantiles - 1);
            float idx = pct * (n - 1);
            int lo = (int) idx;
            int hi = Math.min(lo + 1, n - 1);
            q[i] = values[lo] + (idx - lo) * (values[hi] - values[lo]);
        }

        // Ensure strictly increasing (matches Python build_quantiles)
        float minDiff = Float.MAX_VALUE;
        for (int i = 1; i < nQuantiles; i++)
            if (q[i] > q[i - 1]) minDiff = Math.min(minDiff, q[i] - q[i - 1]);
        if (minDiff == Float.MAX_VALUE) minDiff = 1e-10f;
        for (int i = 1; i < nQuantiles; i++)
            if (q[i] <= q[i - 1]) q[i] = q[i - 1] + minDiff * 0.1f;

        return q;
    }

    /**
     * Sample the synthetic map at world coordinates.
     *
     * @param x1 left world coord (j column in tile space)
     * @param y1 top world coord (i row in tile space)
     * @param x2 exclusive right
     * @param y2 exclusive bottom
     * @return float[5][H][W] where H = y2-y1, W = x2-x1
     *         channels: [elev_sqrt, temp, temp_std, precip, precip_std]
     */
    public float[][][] sample(int x1, int y1, int x2, int y2) {
        int H = y2 - y1;
        int W = x2 - x1;
        float[][] rawChannels = new float[N_CHANNELS][H * W];

        // Sample each channel with quantile transform
        for (int ch = 0; ch < N_CHANNELS; ch++) {
            FastNoiseLite fnl = noises[ch];
            float[] nq = noiseQuantiles[ch];
            float[] dq = dataQuantiles[ch];
            int k = 0;
            // Python: noise is sampled at (Xs, Ys) where Xs = col (x1..x2) and Ys = row (y1..y2)
            // meshgrid(x, y) with x=col-range, y=row-range → result[r][c] = noise(x1+c, y1+r)
            for (int r = 0; r < H; r++) {
                for (int c = 0; c < W; c++) {
                    float noiseVal = fnl.GetNoise(x1 + c, y1 + r);
                    rawChannels[ch][k++] = interp(noiseVal, nq, dq);
                }
            }
        }

        // Reshape to [ch][H][W]
        float[][] ch2d = new float[N_CHANNELS][H * W];
        System.arraycopy(rawChannels[0], 0, ch2d[0], 0, H * W);
        System.arraycopy(rawChannels[1], 0, ch2d[1], 0, H * W);
        System.arraycopy(rawChannels[2], 0, ch2d[2], 0, H * W);
        System.arraycopy(rawChannels[3], 0, ch2d[3], 0, H * W);
        System.arraycopy(rawChannels[4], 0, ch2d[4], 0, H * W);

        // Post-process as per sample_full_synthetic_map
        float[][][] result = new float[N_CHANNELS][H][W];
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float elev = ch2d[0][idx];
                float temp = ch2d[1][idx];
                float tempStd = ch2d[2][idx];
                float precip = ch2d[3][idx];
                float precipStd = ch2d[4][idx];

                // Temp: correct for lapse rate based on elevation
                float lapseRate = -6.5f + 0.0015f * precip;
                lapseRate = Math.max(-9.8f, Math.min(-4.0f, lapseRate)) / 1000.0f;
                temp = temp + lapseRate * Math.max(0.0f, elev);
                temp = Math.max(-10.0f, Math.min(40.0f, temp));

                // Temp std correction
                float baseline = (float) aTempStd * temp + bTempStd;
                float t01 = (tempStd - tempStdP1) / (tempStdP99 - tempStdP1);
                float baselineClipped = Math.max(tempStdP1, -baseline);
                tempStd = t01 * (tempStdP99 - baselineClipped) + baselineClipped + baseline;
                tempStd = Math.max(tempStd, 20.0f);

                // Precip std correction
                precipStd = precipStd * Math.max(0.0f, (185.0f - 0.04111f * precip) / 185.0f);

                // Elevation: signed sqrt transform
                float elevSqrt = (float) (Math.signum(elev) * Math.sqrt(Math.abs(elev)));

                result[0][r][c] = elevSqrt;
                result[1][r][c] = temp;
                result[2][r][c] = tempStd;
                result[3][r][c] = precip;
                result[4][r][c] = precipStd;
            }
        }
        return result;
    }

    /** Linear interpolation matching numpy's np.interp: clamp at boundaries. */
    static float interp(float x, float[] xp, float[] fp) {
        int n = xp.length;
        if (x <= xp[0]) return fp[0];
        if (x >= xp[n - 1]) return fp[n - 1];
        // Binary search for position
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xp[mid] <= x) lo = mid; else hi = mid;
        }
        float t = (x - xp[lo]) / (xp[hi] - xp[lo]);
        return fp[lo] + t * (fp[hi] - fp[lo]);
    }
}
