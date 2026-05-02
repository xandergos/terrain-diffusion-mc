package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.hydro.RiverGridCache;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

public class TerrainDiffusionDensityFunction implements DensityFunction {
    public static final MapCodec<TerrainDiffusionDensityFunction> CODEC =
            MapCodec.unit(TerrainDiffusionDensityFunction::new);

    public static final CodecHolder<TerrainDiffusionDensityFunction> CODEC_HOLDER = CodecHolder.of(CODEC);

    @Override
    public double sample(DensityFunction.NoisePos pos) {
        return compute(pos);
    }

    // Cache for compute() single-sample path (one chunk at a time, thread-local-safe
    // because density functions are called from chunk worker threads sequentially per chunk).
    private int cachedSingleChunkX = Integer.MIN_VALUE;
    private int cachedSingleChunkZ = Integer.MIN_VALUE;
    private int[] cachedSingleDepression = null;

    public double compute(DensityFunction.NoisePos context) {
        int x = context.blockX();
        int z = context.blockZ();
        int y = context.blockY();

        int tileSize  = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);
        int tileX = x >> tileShift;
        int tileZ = z >> tileShift;
        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;

        HeightmapData data = LocalTerrainProvider.getInstance()
                .fetchHeightmap(blockStartZ, blockStartX, blockStartZ + tileSize, blockStartX + tileSize);
        if (data == null || data.heightmap == null) return -y;

        int localX = Math.max(0, Math.min(data.width  - 1, x - blockStartX));
        int localZ = Math.max(0, Math.min(data.height - 1, z - blockStartZ));
        int targetHeight = HeightConverter.convertToMinecraftHeight(data.heightmap[localZ][localX]);

        int cx = x >> 4, cz = z >> 4;
        if (cx != cachedSingleChunkX || cz != cachedSingleChunkZ) {
            cachedSingleChunkX    = cx;
            cachedSingleChunkZ    = cz;
            cachedSingleDepression = RiverGridCache.getDepressionMapForChunk(cx, cz);
        }
        if (cachedSingleDepression != null) {
            int lx = x - (cx << 4), lz = z - (cz << 4);
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16)
                targetHeight += cachedSingleDepression[lz * 16 + lx];
        }

        return targetHeight - y;
    }

    /**
     * Per-fill() invocation context. Caches :
     * - the heightmap tile (unchanged from before)
     * - the river depression map for the current chunk (16×16 int[])
     *
     * fill() is called once per Y-column slice inside a chunk ; X and Z stay
     * constant within a single call , so the chunk key rarely changes.
     */
    private static final class FillContext {
        // Tile cache
        int blockStartX, blockStartZ, blockEndX, blockEndZ;
        HeightmapData data;

        // River depression cache (one 16×16 chunk)
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
        int[] riverDepression; // [lz * 16 + lx], null if not yet loaded

        void update(int x, int z) {
            if (x < blockStartX || x >= blockEndX || z < blockStartZ || z >= blockEndZ)
                initTile(x, z);

            int cx = x >> 4, cz = z >> 4;
            if (cx != chunkX || cz != chunkZ)
                initRiver(cx, cz);
        }

        void init(int x, int z) {
            initTile(x, z);
            initRiver(x >> 4, z >> 4);
        }

        private void initTile(int x, int z) {
            int tileSize  = TerrainDiffusionConfig.tileSize();
            int tileShift = Integer.numberOfTrailingZeros(tileSize);
            int tileX = x >> tileShift;
            int tileZ = z >> tileShift;
            this.blockStartX = tileX << tileShift;
            this.blockStartZ = tileZ << tileShift;
            this.blockEndX   = blockStartX + tileSize;
            this.blockEndZ   = blockStartZ + tileSize;
            this.data = LocalTerrainProvider.getInstance()
                    .fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        }

        private void initRiver(int cx, int cz) {
            this.chunkX = cx;
            this.chunkZ = cz;
            this.riverDepression = RiverGridCache.getDepressionMapForChunk(cx, cz);
        }

        /** Depression in blocks for a given world block coord (negative = carve). */
        int riverDepressionAt(int x, int z) {
            if (riverDepression == null) return 0;
            int lx = x - (chunkX << 4);
            int lz = z - (chunkZ << 4);
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return 0;
            return riverDepression[lz * 16 + lx];
        }
    }

    @Override
    public void fill(double[] densities, DensityFunction.EachApplier applier) {
        if (densities.length == 0) return;

        FillContext ctx = new FillContext();
        DensityFunction.NoisePos pos = applier.at(0);
        ctx.init(pos.blockX(), pos.blockZ());

        for (int i = 0; i < densities.length; i++) {
            pos = applier.at(i);
            int x = pos.blockX();
            int z = pos.blockZ();
            int y = pos.blockY();
            ctx.update(x, z);

            HeightmapData data = ctx.data;
            if (data == null || data.heightmap == null) {
                densities[i] = -y;
                continue;
            }

            int localX = Math.max(0, Math.min(data.width  - 1, x - ctx.blockStartX));
            int localZ = Math.max(0, Math.min(data.height - 1, z - ctx.blockStartZ));

            int targetHeight = HeightConverter
                    .convertToMinecraftHeight(data.heightmap[localZ][localX]);
            targetHeight += ctx.riverDepressionAt(x, z);
            densities[i] = targetHeight - y;
        }
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