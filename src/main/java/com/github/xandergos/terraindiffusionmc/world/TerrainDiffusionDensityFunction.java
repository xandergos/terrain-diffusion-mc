package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public class TerrainDiffusionDensityFunction implements DensityFunction {
    public static final MapCodec<TerrainDiffusionDensityFunction> CODEC =
            MapCodec.unit(TerrainDiffusionDensityFunction::new);

    public static final KeyDispatchDataCodec<TerrainDiffusionDensityFunction> CODEC_HOLDER = KeyDispatchDataCodec.of(CODEC);

    private static final int TILE_SIZE  = TerrainDiffusionConfig.tileSize();
    private static final int TILE_SHIFT = Integer.numberOfTrailingZeros(TILE_SIZE);

    /**
     * Per-thread single-entry cache: last (blockX, blockZ) -> targetHeight.
     *
     * <p>The chunk-gen pipeline samples densities in column-major order, so consecutive
     * compute() calls share (x, z) and only differ in y. Caching the last lookup catches
     * those (and the back-to-back calls vanilla aquifer makes for preliminarySurfaceLevel
     * iteration: 250+ Y samples per column on the first hit).
     *
     * <p>cache_once at the JSON layer covers the chunk-gen MarkerContext path. This
     * ThreadLocal covers the SinglePointContext path used by the aquifer and surface-level
     * iteration, which doesn't carry a marker counter and therefore bypasses cache_once.
     *
     * <p>{@code static} so all parsed instances share the per-thread cache (the codec is
     * MapCodec.unit, so the named density function and any inline reference produce
     * separate instances; without static state each would have its own cache).
     */
    /**
     * Sentinel for the unset cache slot. Long.MIN_VALUE+1 corresponds to
     * (x=Integer.MIN_VALUE+1, z=1) — far outside any reachable world coord.
     */
    private static final long NO_KEY = Long.MIN_VALUE + 1;


    private static final ThreadLocal<long[]> LAST_LOOKUP = ThreadLocal.withInitial(() -> {
        long[] arr = new long[2];
        arr[0] = NO_KEY;
        return arr;
    });

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        int x = context.blockX();
        int z = context.blockZ();
        int y = context.blockY();

        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        long[] cache = LAST_LOOKUP.get();
        if (cache[0] == key) {
            return cache[1] - y;
        }

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

        cache[0] = key;
        cache[1] = targetHeight;
        return targetHeight - y;
    }

    @Override
    public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
        applier.fillAllDirectly(densities, this);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
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
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC_HOLDER;
    }
}
