package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PipelineNoiseFunction implements DensityFunction {
    private final NoiseChannel channel;

    private static final FastNoiseLite TEMP_NOISE, TEMP_NOISE_FINE;
    private static final FastNoiseLite PRECIP_NOISE;

    static {
        TEMP_NOISE = makeFnl(12345, 1f/500f, 3, 2f, 0.5f);
        TEMP_NOISE_FINE = makeFnl(54321, 1f/128f, 2, 2f, 0.5f);
        PRECIP_NOISE = makeFnl(12345, 1f/500f, 5, 2f, 0.5f);
    }

    private static FastNoiseLite makeFnl(int seed, float freq, int oct, float lac, float gain) {
        FastNoiseLite fnl = new FastNoiseLite(seed);
        fnl.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        fnl.SetFrequency(freq);
        fnl.SetFractalType(FastNoiseLite.FractalType.FBm);
        fnl.SetFractalOctaves(oct);
        fnl.SetFractalLacunarity(lac);
        fnl.SetFractalGain(gain);
        return fnl;
    }

    public enum NoiseChannel {
        EROSION,
        CONTINENTS,
        TEMPERATURE,
        VEGETATION,
        DEPTH
    }

    public PipelineNoiseFunction(NoiseChannel channel) {
        this.channel = channel;
    }

    public static MapCodec<PipelineNoiseFunction> codec(NoiseChannel channel) {
        return MapCodec.unit(() -> new PipelineNoiseFunction(channel));
    }

    private final Map<Long, LocalTerrainProvider.HeightmapData> tileCache = new ConcurrentHashMap<>();

    private long pack(int tileX, int tileZ) {
        return (((long) tileX) << 32) | (tileZ & 0xffffffffL);
    }

    private LocalTerrainProvider.HeightmapData getTile(int tileX, int tileZ, int tileSize) {
        long key = pack(tileX, tileZ);

        return tileCache.computeIfAbsent(key, k -> {
            int startX = tileX * tileSize;
            int startZ = tileZ * tileSize;

            int endX = startX + tileSize;
            int endZ = startZ + tileSize;

            return LocalTerrainProvider.getInstance()
                    .fetchHeightmap(startX, startZ, endX, endZ);
        });
    }

    @Override
    public double sample(NoisePos pos) {
        int x = pos.blockX();
        int z = pos.blockZ();

        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileX = Math.floorDiv(x, tileSize);
        int tileZ = Math.floorDiv(z, tileSize);

        int localX = Math.floorMod(x, tileSize);
        int localZ = Math.floorMod(z, tileSize);

        LocalTerrainProvider.HeightmapData data = getTile(tileX, tileZ, tileSize);

        return switch(channel) {
            case EROSION -> computeErosion(localX, localZ, data);
            case CONTINENTS -> computeContinents(localX, localZ, data);
            case TEMPERATURE -> computeTemperature(x, z, localX, localZ, data);
            case VEGETATION -> computeVegetation(x, z, localX, localZ, data);
            case DEPTH -> computeDepth(localX, localZ, data);
        };
    }

    @Override
    public void fill(double[] densities, EachApplier applier) {
        for (int i = 0; i < densities.length; i++) {
            NoisePos pos = applier.at(i);
            sample(pos);
        }
    }

    @Override
    public DensityFunction apply(DensityFunctionVisitor visitor) {
        return this;
    }

    @Override
    public double minValue() {
        return -1;
    }

    @Override
    public double maxValue() {
        return 1;
    }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() {
        return null;
    }

    private double computeTemperature(int globalX, int globalZ, int localX, int localZ, LocalTerrainProvider.HeightmapData data) {
        int idx = localX * data.width + localZ;

        float baseTemp = data.climate[idx];

        float coarse = TEMP_NOISE.GetNoise(globalX, globalZ);
        float fine   = TEMP_NOISE_FINE.GetNoise(globalX, globalZ);

        float tempNoise = 0.4f * coarse + 0.2f * fine;

        return baseTemp + tempNoise;
    }

    private double computeVegetation(int globalX, int globalZ, int localX, int localZ, LocalTerrainProvider.HeightmapData data) {
        float temp = (float) computeTemperature(globalX, globalZ, localX, localZ, data);

        float pn = PRECIP_NOISE.GetNoise(globalX, globalZ);
        float precipFactor = 1.0f + 0.2f * pn;

        float moistureBase = 1.0f - Math.max(0f, (temp - 10f) / 40f);
        float aridity = precipFactor * moistureBase;

        aridity = Math.clamp(aridity, 0f, 1.5f);

        float vegetation = aridity * (0.6f + 0.4f * Math.max(0f, 1f - Math.abs(temp - 15f) / 30f));

        float micro = 0.05f * TEMP_NOISE_FINE.GetNoise(globalX, globalZ);

        return vegetation + micro;
    }

    private double computeContinents(int localX, int localZ, LocalTerrainProvider.HeightmapData data) {
        float elev = data.heightmap[localX][localZ];
        float continentalness;

        if (elev < 0f) {
            continentalness = elev / 512f;
        } else {
            continentalness = elev / 2048f;
        }

        continentalness = Math.clamp(continentalness, -1f, 1f);

        return continentalness;
    }

    private double computeDepth(int localX, int localZ, LocalTerrainProvider.HeightmapData data) {
        float elev = data.heightmap[localX][localZ];
        float depth = elev / 1024f;

        if (depth > 0f) {
            depth *= 0.5f;
        }

        depth = Math.clamp(depth, -1f, 1f);

        return -depth;
    }

    private double computeErosion(int localX, int localZ, LocalTerrainProvider.HeightmapData data) {
        float north = sampleHeight(localX, localZ - 1, data);
        float south = sampleHeight(localX, localZ + 1, data);
        float east  = sampleHeight(localX + 1, localZ, data);
        float west  = sampleHeight(localX - 1, localZ, data);

        float dx = east - west;
        float dz = south - north;
        float slope = (float) Math.sqrt(dx * dx + dz * dz);

        float erosion = 1.0f - Math.min(1.0f, slope / 128f);
        erosion = erosion * 2f - 1f;

        return erosion;
    }

    private float sampleHeight(int globalX, int globalZ, LocalTerrainProvider.HeightmapData data) {
        globalX = Math.clamp(globalX, 0, data.width - 1);
        globalZ = Math.clamp(globalZ, 0, data.height - 1);

        return data.heightmap[globalX][globalZ];
    }
}
