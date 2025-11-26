package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.api.HeightmapApiClient;
import com.github.xandergos.terraindiffusionmc.api.HeightmapApiClient.HeightmapData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.concurrent.ExecutionException;

public class TerrainDiffusionDensityFunction implements DensityFunction {
    private static final int TILE_SHIFT = 8; // 256 blocks
    private static final int TILE_SIZE = 1 << TILE_SHIFT;

    public static final MapCodec<TerrainDiffusionDensityFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Codec.STRING.fieldOf("api_url").orElse("http://localhost:8000").forGetter(f -> f.apiUrl)
        ).apply(instance, TerrainDiffusionDensityFunction::new)
    );

    public static final CodecHolder<TerrainDiffusionDensityFunction> CODEC_HOLDER = CodecHolder.of(CODEC);

    private final String apiUrl;
    private final HeightmapApiClient apiClient;

    public TerrainDiffusionDensityFunction(String apiUrl) {
        this.apiUrl = apiUrl;
        this.apiClient = new HeightmapApiClient(apiUrl);
    }

    @Override
    public double sample(DensityFunction.NoisePos pos) {
        return compute(pos);
    }

    public double compute(DensityFunction.NoisePos context) {
        int x = context.blockX();
        int z = context.blockZ();
        int y = context.blockY();

        int tileX = x >> TILE_SHIFT;
        int tileZ = z >> TILE_SHIFT;

        int blockStartX = tileX << TILE_SHIFT;
        int blockStartZ = tileZ << TILE_SHIFT;
        int blockEndX = blockStartX + TILE_SIZE;
        int blockEndZ = blockStartZ + TILE_SIZE;

        try {
            HeightmapData data = apiClient.fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX).get();
            if (data == null || data.heightmap == null) {
                return -y;
            }

            int localX = x - blockStartX;
            int localZ = z - blockStartZ;
            
            // Clamp
            if (localX < 0) localX = 0;
            if (localZ < 0) localZ = 0;
            if (localX >= data.width) localX = data.width - 1;
            if (localZ >= data.height) localZ = data.height - 1;

            short elevationMeters = data.heightmap[localZ][localX];
            int targetHeight = HeightConverter.convertToMinecraftHeight(elevationMeters);
            
            return targetHeight - y;

        } catch (InterruptedException | ExecutionException e) {
            return -y;
        }
    }

    @Override
    public void fill(double[] densities, DensityFunction.EachApplier applier) {
        applier.fill(densities, this);
    }

    @Override
    public DensityFunction apply(DensityFunction.DensityFunctionVisitor visitor) {
        return visitor.apply(this);
    }

    @Override
    public double minValue() {
        return -64;
    }

    @Override
    public double maxValue() {
        return 1024;
    }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() {
        return CODEC_HOLDER;
    }
}
