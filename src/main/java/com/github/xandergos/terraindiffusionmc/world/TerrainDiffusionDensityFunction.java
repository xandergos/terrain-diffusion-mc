package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

public class TerrainDiffusionDensityFunction implements DensityFunction {
    private static final int TILE_SHIFT = 8; // 256 blocks
    private static final int TILE_SIZE = 1 << TILE_SHIFT;

    public static final MapCodec<TerrainDiffusionDensityFunction> CODEC =
            MapCodec.unit(TerrainDiffusionDensityFunction::new);

    public static final CodecHolder<TerrainDiffusionDensityFunction> CODEC_HOLDER = CodecHolder.of(CODEC);

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

        HeightmapData data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        if (data == null || data.heightmap == null) {
            return -y;
        }

        int localX = Math.max(0, Math.min(data.width  - 1, x - blockStartX));
        int localZ = Math.max(0, Math.min(data.height - 1, z - blockStartZ));

        int targetHeight = HeightConverter.convertToMinecraftHeight(data.heightmap[localZ][localX]);
        return targetHeight - y;
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
