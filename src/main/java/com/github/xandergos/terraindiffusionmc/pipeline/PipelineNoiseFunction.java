package com.github.xandergos.terraindiffusionmc.pipeline;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PipelineNoiseFunction implements DensityFunction {
    private final NoiseChannel channel;

    public enum NoiseChannel {
        EROSION,
        CONTINENTS,
        TEMPERATURE,
        VEGETATION,
        DEPTH
    }

    private final Map<Long, LocalTerrainProvider.HeightmapData> cache = new ConcurrentHashMap<>();

    public LocalTerrainProvider.HeightmapData get(int chunkX, int chunkZ) {
        return cache.get(pack(chunkX, chunkZ));
    }

    public void put(int chunkX, int chunkZ, LocalTerrainProvider.HeightmapData data) {
        cache.put(pack(chunkX, chunkZ), data);
    }

    private long pack(int x, int z) {
        return ((long)x << 32) | (z & 0xffffffffL);
    }

    public PipelineNoiseFunction(NoiseChannel channel) {
        this.channel = channel;
    }

    public static MapCodec<PipelineNoiseFunction> codec(NoiseChannel channel) {
        return MapCodec.unit(() -> new PipelineNoiseFunction(channel));
    }

    @Override
    public double sample(NoisePos pos) {
        int x = pos.blockX();
        int z = pos.blockZ();

        LocalTerrainProvider.HeightmapData data = get(x, z);

        if (data == null) {
            return fallback(channel);
        }

        return switch(channel) {
            case EROSION -> data.noiseChannels.erosion(x, z);
            case CONTINENTS -> data.noiseChannels.continents(x, z);
            case TEMPERATURE -> data.noiseChannels.temperature(x, z);
            case VEGETATION -> data.noiseChannels.vegetation(x, z);
            case DEPTH -> data.noiseChannels.depth(x, z);
        };
    }

    private double fallback(NoiseChannel channel) {
        return switch (channel) {
            case CONTINENTS -> 0.0;
            case TEMPERATURE -> 0.5;
            case EROSION -> 0.0;
            case VEGETATION -> 0.5;
            case DEPTH -> 0.0;
        };
    }

    @Override
    public void fill(double[] densities, EachApplier applier) {

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
}
