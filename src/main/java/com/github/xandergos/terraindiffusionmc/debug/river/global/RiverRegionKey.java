package com.github.xandergos.terraindiffusionmc.debug.river.global;

/**
 * Stable cache key for a global river generation region.
 */
public record RiverRegionKey(
        long seed,
        int scale,
        int regionX,
        int regionZ,
        int regionSizeBlocks
) {
    public RiverRegionKey {
        if (regionSizeBlocks <= 0) {
            throw new IllegalArgumentException("regionSizeBlocks must be positive");
        }
    }

    public int blockStartX() {
        return regionX * regionSizeBlocks;
    }

    public int blockStartZ() {
        return regionZ * regionSizeBlocks;
    }

    public int blockEndXExclusive() {
        return blockStartX() + regionSizeBlocks;
    }

    public int blockEndZExclusive() {
        return blockStartZ() + regionSizeBlocks;
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return blockX >= blockStartX()
                && blockZ >= blockStartZ()
                && blockX < blockEndXExclusive()
                && blockZ < blockEndZExclusive();
    }

    public static RiverRegionKey fromBlock(long seed, int scale, int blockX, int blockZ, int regionSizeBlocks) {
        return new RiverRegionKey(
                seed,
                scale,
                Math.floorDiv(blockX, regionSizeBlocks),
                Math.floorDiv(blockZ, regionSizeBlocks),
                regionSizeBlocks
        );
    }

    public static RiverRegionKey fromChunk(long seed, int scale, int chunkX, int chunkZ, int regionSizeBlocks) {
        return fromBlock(seed, scale, chunkX * 16, chunkZ * 16, regionSizeBlocks);
    }
}
