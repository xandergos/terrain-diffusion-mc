package com.github.xandergos.terraindiffusionmc.debug.river.global;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Region cache and query facade for the global river network.
 */
public final class GlobalRiverNetworkProvider {
    public static final int DEFAULT_REGION_SIZE_BLOCKS = 2048;
    public static final int DEFAULT_REGION_HALO_BLOCKS = 512;
    public static final int DEFAULT_CHUNK_QUERY_PADDING_BLOCKS = 32;
    public static final int DEFAULT_STITCH_RADIUS_REGIONS = 1;

    private final RegionBuilder regionBuilder;
    private final Executor executor;
    private final Map<RiverRegionKey, CompletableFuture<RiverRegionNetwork>> regionCache = new ConcurrentHashMap<>();
    private final Map<StitchedKey, CompletableFuture<RiverRegionNetwork>> stitchedRegionCache = new ConcurrentHashMap<>();

    public GlobalRiverNetworkProvider() {
        this(new RiverRegionBuilder(DEFAULT_REGION_HALO_BLOCKS), ForkJoinPool.commonPool());
    }

    public GlobalRiverNetworkProvider(RegionBuilder regionBuilder) {
        this(regionBuilder, ForkJoinPool.commonPool());
    }

    public GlobalRiverNetworkProvider(RegionBuilder regionBuilder, Executor executor) {
        if (regionBuilder == null) {
            throw new IllegalArgumentException("regionBuilder cannot be null");
        }
        this.regionBuilder = regionBuilder;
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
    }

    public static GlobalRiverNetworkProvider createDefault() {
        return new GlobalRiverNetworkProvider(new RiverRegionBuilder(DEFAULT_REGION_HALO_BLOCKS), ForkJoinPool.commonPool());
    }

    public RiverRegionNetwork getOrBuildRegion(long seed, int scale, int regionX, int regionZ) {
        return getOrBuildRegion(new RiverRegionKey(seed, scale, regionX, regionZ, DEFAULT_REGION_SIZE_BLOCKS));
    }

    public RiverRegionNetwork getOrBuildRegion(RiverRegionKey key) {
        return getOrBuildRegionAsync(key).join();
    }

    public CompletableFuture<RiverRegionNetwork> getOrBuildRegionAsync(RiverRegionKey key) {
        return regionCache.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> regionBuilder.build(key), executor));
    }

    public RiverRegionNetwork getOrBuildStitchedRegion(long seed, int scale, int regionX, int regionZ) {
        return getOrBuildStitchedRegion(new RiverRegionKey(seed, scale, regionX, regionZ, DEFAULT_REGION_SIZE_BLOCKS), DEFAULT_STITCH_RADIUS_REGIONS);
    }

    public RiverRegionNetwork getOrBuildStitchedRegion(RiverRegionKey centerKey, int radiusRegions) {
        return getOrBuildStitchedRegionAsync(centerKey, radiusRegions).join();
    }

    public CompletableFuture<RiverRegionNetwork> getOrBuildStitchedRegionAsync(RiverRegionKey centerKey, int radiusRegions) {
        int radius = Math.max(0, radiusRegions);
        StitchedKey stitchedKey = new StitchedKey(centerKey, radius);
        return stitchedRegionCache.computeIfAbsent(stitchedKey, ignored -> CompletableFuture.supplyAsync(
                () -> buildStitchedRegion(centerKey, radius), executor));
    }

    private RiverRegionNetwork buildStitchedRegion(RiverRegionKey centerKey, int radiusRegions) {
        List<RiverRegionNetwork> regions = new ArrayList<>();
        for (int rz = centerKey.regionZ() - radiusRegions; rz <= centerKey.regionZ() + radiusRegions; rz++) {
            for (int rx = centerKey.regionX() - radiusRegions; rx <= centerKey.regionX() + radiusRegions; rx++) {
                regions.add(getOrBuildRegion(new RiverRegionKey(
                        centerKey.seed(),
                        centerKey.scale(),
                        rx,
                        rz,
                        centerKey.regionSizeBlocks()
                )));
            }
        }

        RiverNetwork stitched = RiverNetworkStitcher.stitch(regions, centerKey.seed());
        RiverNetwork hydrology = RiverNetworkHydrology.recalculate(stitched);
        return new RiverRegionNetwork(centerKey, DEFAULT_REGION_HALO_BLOCKS, hydrology);
    }

    public List<RiverSegment> queryChunk(long seed, int scale, int chunkX, int chunkZ) {
        return queryChunk(seed, scale, chunkX, chunkZ, DEFAULT_CHUNK_QUERY_PADDING_BLOCKS, DEFAULT_REGION_SIZE_BLOCKS);
    }

    public List<RiverSegment> queryChunk(long seed, int scale, int chunkX, int chunkZ, int paddingBlocks, int regionSizeBlocks) {
        int minX = chunkX * 16 - paddingBlocks;
        int minZ = chunkZ * 16 - paddingBlocks;
        int maxX = chunkX * 16 + 16 + paddingBlocks;
        int maxZ = chunkZ * 16 + 16 + paddingBlocks;
        return queryAabb(seed, scale, minX, minZ, maxX, maxZ, regionSizeBlocks);
    }

    public List<RiverSegment> queryAabb(long seed, int scale, int minX, int minZ, int maxX, int maxZ, int regionSizeBlocks) {
        int minRegionX = Math.floorDiv(minX, regionSizeBlocks);
        int maxRegionX = Math.floorDiv(maxX - 1, regionSizeBlocks);
        int minRegionZ = Math.floorDiv(minZ, regionSizeBlocks);
        int maxRegionZ = Math.floorDiv(maxZ - 1, regionSizeBlocks);

        Map<Long, RiverSegment> resultById = new LinkedHashMap<>();
        for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
            for (int rx = minRegionX; rx <= maxRegionX; rx++) {
                RiverRegionNetwork region = getOrBuildRegion(new RiverRegionKey(seed, scale, rx, rz, regionSizeBlocks));
                for (RiverSegment segment : region.network().queryAabb(minX, minZ, maxX, maxZ)) {
                    resultById.putIfAbsent(segment.id(), segment);
                }
            }
        }

        return new ArrayList<>(resultById.values());
    }

    public List<RiverSegment> queryStitchedChunk(long seed, int scale, int chunkX, int chunkZ) {
        return queryStitchedChunk(seed, scale, chunkX, chunkZ, DEFAULT_CHUNK_QUERY_PADDING_BLOCKS, DEFAULT_REGION_SIZE_BLOCKS);
    }

    public List<RiverSegment> queryStitchedChunk(long seed, int scale, int chunkX, int chunkZ, int paddingBlocks, int regionSizeBlocks) {
        int minX = chunkX * 16 - paddingBlocks;
        int minZ = chunkZ * 16 - paddingBlocks;
        int maxX = chunkX * 16 + 16 + paddingBlocks;
        int maxZ = chunkZ * 16 + 16 + paddingBlocks;
        return queryStitchedAabb(seed, scale, minX, minZ, maxX, maxZ, regionSizeBlocks);
    }

    public List<RiverSegment> queryStitchedAabb(long seed, int scale, int minX, int minZ, int maxX, int maxZ, int regionSizeBlocks) {
        int centerX = Math.floorDiv(minX + maxX, 2);
        int centerZ = Math.floorDiv(minZ + maxZ, 2);
        RiverRegionKey centerKey = RiverRegionKey.fromBlock(seed, scale, centerX, centerZ, regionSizeBlocks);
        RiverRegionNetwork stitched = getOrBuildStitchedRegion(centerKey, DEFAULT_STITCH_RADIUS_REGIONS);
        return stitched.network().queryAabb(minX, minZ, maxX, maxZ);
    }

    public int cachedRegionCount() {
        return regionCache.size();
    }

    public int cachedStitchedRegionCount() {
        return stitchedRegionCache.size();
    }

    public void clearCache() {
        regionCache.clear();
        stitchedRegionCache.clear();
    }

    @FunctionalInterface
    public interface RegionBuilder {
        RiverRegionNetwork build(RiverRegionKey key);
    }

    private record StitchedKey(RiverRegionKey centerKey, int radiusRegions) {
    }
}
