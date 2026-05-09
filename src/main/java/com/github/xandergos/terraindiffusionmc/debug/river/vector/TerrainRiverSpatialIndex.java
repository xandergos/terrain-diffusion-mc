package com.github.xandergos.terraindiffusionmc.debug.river.vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TerrainRiverSpatialIndex {
    private final TerrainRiverNetwork network;
    private final int cellSizeBlocks;
    private final Map<CellKey, Cell> cells = new HashMap<>();

    private TerrainRiverSpatialIndex(TerrainRiverNetwork network, int cellSizeBlocks) {
        this.network = network;
        this.cellSizeBlocks = Math.max(1, cellSizeBlocks);
        build();
    }

    public static TerrainRiverSpatialIndex build(TerrainRiverNetwork network, int cellSizeBlocks) {
        return new TerrainRiverSpatialIndex(network, cellSizeBlocks);
    }

    public TerrainRiverNetwork network() {
        return network;
    }

    public int cellSizeBlocks() {
        return cellSizeBlocks;
    }

    public List<Cell> cells() {
        return List.copyOf(cells.values());
    }

    public List<TerrainRiverNetwork.Segment> queryChunk(int chunkX, int chunkZ, int paddingBlocks) {
        int minX = chunkX * 16 - paddingBlocks;
        int minZ = chunkZ * 16 - paddingBlocks;
        int maxX = chunkX * 16 + 16 + paddingBlocks;
        int maxZ = chunkZ * 16 + 16 + paddingBlocks;
        return queryAabb(minX, minZ, maxX, maxZ);
    }

    public List<TerrainRiverNetwork.Segment> queryAabb(double minX, double minZ, double maxX, double maxZ) {
        int minCellX = cellCoord(minX);
        int maxCellX = cellCoord(maxX);
        int minCellZ = cellCoord(minZ);
        int maxCellZ = cellCoord(maxZ);

        Set<Integer> segmentIds = new HashSet<>();
        List<TerrainRiverNetwork.Segment> result = new ArrayList<>();

        for (int cz = minCellZ; cz <= maxCellZ; cz++) {
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                Cell cell = cells.get(new CellKey(cx, cz));
                if (cell == null) {
                    continue;
                }

                for (TerrainRiverNetwork.Segment segment : cell.segments()) {
                    if (segmentIds.add(segment.id()) && segmentIntersectsAabb(segment, minX, minZ, maxX, maxZ)) {
                        result.add(segment);
                    }
                }
            }
        }

        return result;
    }

    private void build() {
        for (TerrainRiverNetwork.Segment segment : network.segments()) {
            Bounds bounds = segmentBounds(segment);
            int minCellX = cellCoord(bounds.minX());
            int maxCellX = cellCoord(bounds.maxX());
            int minCellZ = cellCoord(bounds.minZ());
            int maxCellZ = cellCoord(bounds.maxZ());

            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                for (int cx = minCellX; cx <= maxCellX; cx++) {
                    final int cellX = cx;
                    final int cellZ = cz;
                    final int minBlockX = cellX * cellSizeBlocks;
                    final int minBlockZ = cellZ * cellSizeBlocks;
                    final int maxBlockX = minBlockX + cellSizeBlocks;
                    final int maxBlockZ = minBlockZ + cellSizeBlocks;

                    CellKey key = new CellKey(cellX, cellZ);
                    Cell cell = cells.computeIfAbsent(key, ignored -> new Cell(
                            cellX,
                            cellZ,
                            minBlockX,
                            minBlockZ,
                            maxBlockX,
                            maxBlockZ,
                            new ArrayList<>(),
                            Integer.MIN_VALUE
                    ));
                    cell.add(segment, bounds.maxSurfaceY());
                }
            }
        }
    }

    private Bounds segmentBounds(TerrainRiverNetwork.Segment segment) {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        int maxSurfaceY = Integer.MIN_VALUE;

        for (TerrainRiverNetwork.Point point : segment.points()) {
            minX = Math.min(minX, point.worldX());
            minZ = Math.min(minZ, point.worldZ());
            maxX = Math.max(maxX, point.worldX());
            maxZ = Math.max(maxZ, point.worldZ());
            maxSurfaceY = Math.max(maxSurfaceY, point.surfaceY());
        }

        return new Bounds(minX, minZ, maxX, maxZ, maxSurfaceY);
    }

    private boolean segmentIntersectsAabb(TerrainRiverNetwork.Segment segment, double minX, double minZ, double maxX, double maxZ) {
        Bounds bounds = segmentBounds(segment);
        return bounds.maxX() >= minX && bounds.minX() <= maxX && bounds.maxZ() >= minZ && bounds.minZ() <= maxZ;
    }

    private int cellCoord(double blockCoord) {
        return Math.floorDiv((int) Math.floor(blockCoord), cellSizeBlocks);
    }

    private record CellKey(int x, int z) {
    }

    private record Bounds(double minX, double minZ, double maxX, double maxZ, int maxSurfaceY) {
    }

    public static final class Cell {
        private final int cellX;
        private final int cellZ;
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;
        private final List<TerrainRiverNetwork.Segment> segments;
        private int maxSurfaceY;

        private Cell(int cellX, int cellZ, int minX, int minZ, int maxX, int maxZ, List<TerrainRiverNetwork.Segment> segments, int maxSurfaceY) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.segments = segments;
            this.maxSurfaceY = maxSurfaceY;
        }

        private void add(TerrainRiverNetwork.Segment segment, int segmentMaxSurfaceY) {
            segments.add(segment);
            maxSurfaceY = Math.max(maxSurfaceY, segmentMaxSurfaceY);
        }

        public int cellX() { return cellX; }
        public int cellZ() { return cellZ; }
        public int minX() { return minX; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxZ() { return maxZ; }
        public int maxSurfaceY() { return maxSurfaceY == Integer.MIN_VALUE ? 64 : maxSurfaceY; }
        public int segmentCount() { return segments.size(); }
        public List<TerrainRiverNetwork.Segment> segments() { return List.copyOf(segments); }
    }
}
