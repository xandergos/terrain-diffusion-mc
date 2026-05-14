package com.github.xandergos.terraindiffusionmc.client.debug;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.debug.TerrainBaseTile;
import com.github.xandergos.terraindiffusionmc.debug.cost.TerrainCostTile;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowConfig;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowTile;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverConfig;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverTile;
import com.github.xandergos.terraindiffusionmc.debug.river.global.GlobalRiverNetworkProvider;
import com.github.xandergos.terraindiffusionmc.debug.river.global.RiverNetwork;
import com.github.xandergos.terraindiffusionmc.debug.river.global.RiverNode;
import com.github.xandergos.terraindiffusionmc.debug.river.global.RiverPoint;
import com.github.xandergos.terraindiffusionmc.debug.river.global.RiverSegment;
import com.github.xandergos.terraindiffusionmc.debug.river.overlay.TerrainRiverChunkOverlay;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverNetwork;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverSpatialIndex;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverVectorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gizmos.Gizmos;

public final class TerrainDebugOverlayRendererCore {
    private static final double CELL_OVERLAP = 0.035D;

    private TerrainDebugOverlayRendererCore() {
    }

    public static void render(LevelRenderer levelRenderer) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        TerrainDebugOverlayMode mode = TerrainDebugOverlayState.mode();
        if (mode == TerrainDebugOverlayMode.OFF) {
            return;
        }

        if (levelRenderer == null) {
            return;
        }

        int tileSize = TerrainDiffusionConfig.tileSize();
        int playerBlockX = Mth.floor(client.player.getX());
        int playerBlockZ = Mth.floor(client.player.getZ());
        int centerTileX = Math.floorDiv(playerBlockX, tileSize);
        int centerTileZ = Math.floorDiv(playerBlockZ, tileSize);
        int radius = TerrainDebugOverlayState.radiusTiles();

        try (Gizmos.TemporaryCollection ignored = levelRenderer.collectPerFrameGizmos()) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int blockStartX = (centerTileX + dx) * tileSize;
                    int blockStartZ = (centerTileZ + dz) * tileSize;

                    if (mode.isCostMode()) {
                        TerrainDebugTileClientCache.requestCost(blockStartZ, blockStartX, tileSize, tileSize);
                        TerrainCostTile tile = TerrainDebugTileClientCache.getCostIfReady(blockStartZ, blockStartX, tileSize, tileSize);
                        if (tile != null) {
                            emitCostTile(tile, mode);
                        }
                    } else if (mode.isFlowMode()) {
                        TerrainDebugTileClientCache.requestFlow(blockStartZ, blockStartX, tileSize, tileSize);
                        TerrainFlowTile tile = TerrainDebugTileClientCache.getFlowIfReady(blockStartZ, blockStartX, tileSize, tileSize);
                        if (tile != null) {
                            emitFlowTile(tile, mode);
                        }
                    } else if (mode.isGlobalRiverMode()) {
                        TerrainDebugTileClientCache.requestGlobalRiverNetwork(playerBlockX, playerBlockZ);
                        RiverNetwork network = TerrainDebugTileClientCache.getGlobalRiverNetworkIfReady(playerBlockX, playerBlockZ);
                        if (network != null) {
                            emitGlobalRiverNetwork(network, mode, playerBlockX, playerBlockZ, tileSize, radius);
                        }
                    } else if (mode.isRiverChunkOverlayMode()) {
                        TerrainDebugTileClientCache.requestRiverTileOverlay(blockStartZ, blockStartX, tileSize, tileSize);
                        TerrainRiverChunkOverlay overlay = TerrainDebugTileClientCache.getRiverTileOverlayIfReady(blockStartZ, blockStartX, tileSize, tileSize);
                        if (overlay != null) {
                            emitRiverChunkOverlay(overlay, mode);
                        }
                    } else if (mode.isRiverSpatialMode()) {
                        TerrainDebugTileClientCache.requestRiverSpatialIndex(blockStartZ, blockStartX, tileSize, tileSize);
                        TerrainRiverSpatialIndex index = TerrainDebugTileClientCache.getRiverSpatialIndexIfReady(blockStartZ, blockStartX, tileSize, tileSize);
                        if (index != null) {
                            emitRiverSpatialIndex(index, mode, playerBlockX, playerBlockZ);
                        }
                    } else if (mode.isRiverVectorMode()) {
                        TerrainDebugTileClientCache.requestRiverVector(blockStartZ, blockStartX, tileSize, tileSize);
                        TerrainRiverNetwork network = TerrainDebugTileClientCache.getRiverVectorIfReady(blockStartZ, blockStartX, tileSize, tileSize);
                        if (network != null) {
                            emitRiverVectorNetwork(network, mode);
                        }
                    } else if (mode.isRiverMode()) {
                        TerrainDebugTileClientCache.requestRiver(blockStartZ, blockStartX, tileSize, tileSize);
                        TerrainRiverTile tile = TerrainDebugTileClientCache.getRiverIfReady(blockStartZ, blockStartX, tileSize, tileSize);
                        if (tile != null) {
                            emitRiverTile(tile, mode);
                        }
                    } else {
                        TerrainDebugTileClientCache.request(blockStartZ, blockStartX, tileSize, tileSize);
                        TerrainBaseTile tile = TerrainDebugTileClientCache.getIfReady(blockStartZ, blockStartX, tileSize, tileSize);
                        if (tile != null) {
                            emitBaseTile(tile, mode);
                        }
                    }
                }
            }
        }
    }

    private static void emitBaseTile(TerrainBaseTile tile, TerrainDebugOverlayMode mode) {
        int stride = TerrainDebugOverlayState.stride();
        for (int z = 0; z < tile.height(); z += stride) {
            int zEndExclusive = Math.min(z + stride, tile.height());
            for (int x = 0; x < tile.width(); x += stride) {
                int xEndExclusive = Math.min(x + stride, tile.width());
                int y = baseCellFlatY(tile, x, z, xEndExclusive, zEndExclusive, mode);
                int sampleX = sampleCoord(x, xEndExclusive, tile.width());
                int sampleZ = sampleCoord(z, zEndExclusive, tile.height());
                emitQuad(tile.blockStartX(), tile.blockStartZ(), x, z, xEndExclusive, zEndExclusive, y, baseColor(tile, sampleX, sampleZ, mode));
            }
        }
    }

    private static void emitCostTile(TerrainCostTile tile, TerrainDebugOverlayMode mode) {
        int stride = TerrainDebugOverlayState.stride();
        for (int z = 0; z < tile.height(); z += stride) {
            int zEndExclusive = Math.min(z + stride, tile.height());
            for (int x = 0; x < tile.width(); x += stride) {
                int xEndExclusive = Math.min(x + stride, tile.width());
                int y = costCellFlatY(tile, x, z, xEndExclusive, zEndExclusive);
                int sampleX = sampleCoord(x, xEndExclusive, tile.width());
                int sampleZ = sampleCoord(z, zEndExclusive, tile.height());
                emitQuad(tile.blockStartX(), tile.blockStartZ(), x, z, xEndExclusive, zEndExclusive, y, costColor(tile, sampleX, sampleZ, mode));
            }
        }
    }

    private static void emitFlowTile(TerrainFlowTile tile, TerrainDebugOverlayMode mode) {
        int stride = TerrainDebugOverlayState.stride();
        for (int z = 0; z < tile.height(); z += stride) {
            int zEndExclusive = Math.min(z + stride, tile.height());
            for (int x = 0; x < tile.width(); x += stride) {
                int xEndExclusive = Math.min(x + stride, tile.width());
                int y = flowCellFlatY(tile, x, z, xEndExclusive, zEndExclusive);
                int sampleX = sampleCoord(x, xEndExclusive, tile.width());
                int sampleZ = sampleCoord(z, zEndExclusive, tile.height());
                emitQuad(tile.blockStartX(), tile.blockStartZ(), x, z, xEndExclusive, zEndExclusive, y, flowColor(tile, sampleX, sampleZ, mode));
            }
        }
    }

    private static void emitGlobalRiverNetwork(RiverNetwork network, TerrainDebugOverlayMode mode, int playerBlockX, int playerBlockZ, int tileSize, int radiusTiles) {
        int queryRadius = Math.max(256, tileSize * Math.max(1, radiusTiles) + 256);
        int minX = playerBlockX - queryRadius;
        int minZ = playerBlockZ - queryRadius;
        int maxX = playerBlockX + queryRadius;
        int maxZ = playerBlockZ + queryRadius;

        if (mode == TerrainDebugOverlayMode.GLOBAL_RIVER_REGION_BORDERS) {
            emitGlobalRegionBorders(playerBlockX, playerBlockZ);
        }

        float maxDischarge = 1.0F;
        float maxWidth = 1.0F;
        float maxDepth = 1.0F;
        for (RiverSegment segment : network.queryAabb(minX, minZ, maxX, maxZ)) {
            maxDischarge = Math.max(maxDischarge, segment.discharge());
            maxWidth = Math.max(maxWidth, segment.maxWidthBlocks());
            maxDepth = Math.max(maxDepth, segment.depthBlocks());
        }

        if (mode == TerrainDebugOverlayMode.GLOBAL_RIVER_NODES) {
            for (RiverSegment segment : network.queryAabb(minX, minZ, maxX, maxZ)) {
                emitGlobalSegment(segment, TerrainDebugOverlayMode.GLOBAL_RIVER_LINES, maxDischarge, maxWidth, maxDepth, true);
            }
            for (RiverNode node : network.nodes()) {
                if (node.worldX() >= minX && node.worldX() <= maxX && node.worldZ() >= minZ && node.worldZ() <= maxZ) {
                    emitGlobalNode(node);
                }
            }
            return;
        }

        for (RiverSegment segment : network.queryAabb(minX, minZ, maxX, maxZ)) {
            emitGlobalSegment(segment, mode, maxDischarge, maxWidth, maxDepth, false);
        }
    }

    private static void emitGlobalSegment(RiverSegment segment, TerrainDebugOverlayMode mode, float maxDischarge, float maxWidth, float maxDepth, boolean muted) {
        if (segment.polyline().size() < 2) {
            return;
        }

        int color = muted ? argb(105, 70, 130, 160) : globalRiverColor(segment, mode, maxDischarge, maxWidth, maxDepth);
        float width = muted ? 0.20F : globalRiverLineWidth(segment, mode, maxDischarge, maxWidth, maxDepth);
        for (int i = 0; i < segment.polyline().size() - 1; i++) {
            Vec3 a = globalPoint(segment.polyline().get(i));
            Vec3 b = globalPoint(segment.polyline().get(i + 1));
            Gizmos.line(a, b, color, width);
        }
    }

    private static Vec3 globalPoint(RiverPoint point) {
        return new Vec3(
                point.worldX(),
                point.surfaceY() + TerrainDebugOverlayState.yOffset() + 1.35D,
                point.worldZ()
        );
    }

    private static void emitGlobalNode(RiverNode node) {
        Vec3 point = new Vec3(
                node.worldX(),
                node.surfaceY() + TerrainDebugOverlayState.yOffset() + 1.65D,
                node.worldZ()
        );
        Gizmos.point(point, globalNodeColor(node.type()), globalNodeSize(node.type()));
    }

    private static int globalRiverColor(RiverSegment segment, TerrainDebugOverlayMode mode, float maxDischarge, float maxWidth, float maxDepth) {
        return switch (mode) {
            case GLOBAL_RIVER_DISCHARGE -> riverPreviewColor(segment.discharge(), maxDischarge);
            case GLOBAL_RIVER_WIDTH -> riverWidthVectorColor(segment.maxWidthBlocks());
            case GLOBAL_RIVER_DEPTH -> depthColor(segment.depthBlocks(), maxDepth);
            case GLOBAL_RIVER_REGION_BORDERS -> argb(255, 70, 235, 255);
            default -> argb(255, 40, 230, 255);
        };
    }

    private static float globalRiverLineWidth(RiverSegment segment, TerrainDebugOverlayMode mode, float maxDischarge, float maxWidth, float maxDepth) {
        return switch (mode) {
            case GLOBAL_RIVER_DISCHARGE -> {
                float n = contrast(logNormalizedAccumulation(segment.discharge(), maxDischarge));
                yield Mth.clamp(0.42F + n * 2.70F, 0.42F, 3.20F);
            }
            case GLOBAL_RIVER_WIDTH -> {
                float n = maxWidth <= 0.0F ? 0.0F : clamp01(segment.maxWidthBlocks() / maxWidth);
                yield Mth.clamp(0.42F + (float) Math.sqrt(n) * 3.00F, 0.42F, 3.40F);
            }
            case GLOBAL_RIVER_DEPTH -> {
                float n = maxDepth <= 0.0F ? 0.0F : clamp01(segment.depthBlocks() / maxDepth);
                yield Mth.clamp(0.42F + n * 2.20F, 0.42F, 2.80F);
            }
            default -> 0.62F;
        };
    }

    private static int globalNodeColor(RiverNode.NodeType type) {
        return switch (type) {
            case SOURCE -> argb(255, 65, 255, 225);
            case CONFLUENCE -> argb(255, 255, 185, 20);
            case OUTLET, OCEAN_OUTLET -> argb(255, 255, 65, 180);
            case LAKE_INLET, LAKE_OUTLET -> argb(255, 95, 185, 255);
            case BOUNDARY -> argb(255, 210, 115, 255);
            case INTERNAL -> argb(255, 230, 230, 230);
        };
    }

    private static float globalNodeSize(RiverNode.NodeType type) {
        return switch (type) {
            case CONFLUENCE -> 2.75F;
            case OUTLET, OCEAN_OUTLET -> 2.30F;
            case SOURCE -> 1.95F;
            case LAKE_INLET, LAKE_OUTLET -> 2.10F;
            case BOUNDARY -> 1.70F;
            case INTERNAL -> 1.20F;
        };
    }

    private static void emitGlobalRegionBorders(int playerBlockX, int playerBlockZ) {
        int regionSize = GlobalRiverNetworkProvider.DEFAULT_REGION_SIZE_BLOCKS;
        int centerRegionX = Math.floorDiv(playerBlockX, regionSize);
        int centerRegionZ = Math.floorDiv(playerBlockZ, regionSize);
        int y = 220;
        int color = argb(215, 255, 240, 80);
        float width = 0.34F;

        for (int rz = centerRegionZ - 1; rz <= centerRegionZ + 1; rz++) {
            for (int rx = centerRegionX - 1; rx <= centerRegionX + 1; rx++) {
                int minX = rx * regionSize;
                int minZ = rz * regionSize;
                int maxX = minX + regionSize;
                int maxZ = minZ + regionSize;
                Vec3 nw = new Vec3(minX, y, minZ);
                Vec3 ne = new Vec3(maxX, y, minZ);
                Vec3 se = new Vec3(maxX, y, maxZ);
                Vec3 sw = new Vec3(minX, y, maxZ);
                Gizmos.line(nw, ne, color, width);
                Gizmos.line(ne, se, color, width);
                Gizmos.line(se, sw, color, width);
                Gizmos.line(sw, nw, color, width);
            }
        }
    }

    private static void emitRiverVectorNetwork(TerrainRiverNetwork network, TerrainDebugOverlayMode mode) {
        if (mode == TerrainDebugOverlayMode.RIVER_VECTOR_NODES) {
            for (TerrainRiverNetwork.Segment segment : network.segments()) {
                emitVectorSegment(network, segment, TerrainDebugOverlayMode.RIVER_VECTOR_LINES, true);
            }

            for (TerrainRiverNetwork.Node node : network.nodes()) {
                emitVectorNode(node);
            }

            return;
        }

        for (TerrainRiverNetwork.Segment segment : network.segments()) {
            emitVectorSegment(network, segment, mode, false);
        }
    }

    private static void emitVectorSegment(TerrainRiverNetwork network, TerrainRiverNetwork.Segment segment, TerrainDebugOverlayMode mode, boolean muted) {
        if (segment.points().size() < 2) {
            return;
        }

        int color = muted ? argb(110, 70, 125, 155) : riverVectorColor(network, segment, mode);
        float width = muted ? 0.18F : riverVectorLineWidth(network, segment, mode);

        for (int i = 0; i < segment.points().size() - 1; i++) {
            Vec3 a = vectorPoint(segment.points().get(i));
            Vec3 b = vectorPoint(segment.points().get(i + 1));
            Gizmos.line(a, b, color, width);
        }
    }

    private static void emitVectorNode(TerrainRiverNetwork.Node node) {
        Vec3 p = vectorNodePoint(node);
        Gizmos.point(p, vectorNodeColor(node.type()), vectorNodeSize(node.type()));
    }

    private static Vec3 vectorPoint(TerrainRiverNetwork.Point point) {
        return new Vec3(
                point.worldX(),
                point.surfaceY() + TerrainDebugOverlayState.yOffset() + 0.78D,
                point.worldZ()
        );
    }

    private static Vec3 vectorNodePoint(TerrainRiverNetwork.Node node) {
        return new Vec3(
                node.worldX(),
                node.surfaceY() + TerrainDebugOverlayState.yOffset() + 1.18D,
                node.worldZ()
        );
    }

    private static int riverVectorColor(TerrainRiverNetwork network, TerrainRiverNetwork.Segment segment, TerrainDebugOverlayMode mode) {
        return switch (mode) {
            case RIVER_VECTOR_LINES -> argb(255, 30, 225, 255);
            case RIVER_VECTOR_WIDTH -> riverWidthVectorColor(segment.maxWidthBlocks());
            case RIVER_VECTOR_FLOW -> riverPreviewColor(segment.maxAccumulation(), network.maxAccumulation());
            default -> argb(255, 30, 225, 255);
        };
    }

    private static float riverVectorLineWidth(TerrainRiverNetwork network, TerrainRiverNetwork.Segment segment, TerrainDebugOverlayMode mode) {
        return switch (mode) {
            case RIVER_VECTOR_LINES -> 0.56F;
            case RIVER_VECTOR_WIDTH -> {
                float n = widthVisualNormalized(segment.maxWidthBlocks());
                yield Mth.clamp(0.34F + n * 2.55F, 0.34F, 2.90F);
            }
            case RIVER_VECTOR_FLOW -> {
                float n = contrast(logNormalizedAccumulation(segment.maxAccumulation(), network.maxAccumulation()));
                yield Mth.clamp(0.34F + n * 2.25F, 0.34F, 2.55F);
            }
            default -> 0.56F;
        };
    }

    private static int vectorNodeColor(TerrainRiverNetwork.NodeType type) {
        return switch (type) {
            case SOURCE -> argb(255, 70, 255, 225);
            case CONFLUENCE -> argb(255, 255, 185, 25);
            case OUTLET -> argb(255, 255, 55, 180);
            case BOUNDARY -> argb(255, 210, 110, 255);
            case INTERNAL -> argb(255, 235, 235, 235);
        };
    }

    private static float vectorNodeSize(TerrainRiverNetwork.NodeType type) {
        return switch (type) {
            case SOURCE -> 1.85F;
            case CONFLUENCE -> 2.55F;
            case OUTLET -> 2.20F;
            case BOUNDARY -> 1.65F;
            case INTERNAL -> 1.30F;
        };
    }

    private static void emitRiverSpatialIndex(TerrainRiverSpatialIndex index, TerrainDebugOverlayMode mode, int playerBlockX, int playerBlockZ) {
        if (mode == TerrainDebugOverlayMode.RIVER_SPATIAL_INDEX_CELLS) {
            for (TerrainRiverSpatialIndex.Cell cell : index.cells()) {
                emitSpatialCell(cell, false);
            }
            return;
        }

        int chunkX = Math.floorDiv(playerBlockX, 16);
        int chunkZ = Math.floorDiv(playerBlockZ, 16);
        int padding = TerrainRiverVectorConfig.CHUNK_QUERY_PADDING_BLOCKS;
        int minX = chunkX * 16 - padding;
        int minZ = chunkZ * 16 - padding;
        int maxX = chunkX * 16 + 16 + padding;
        int maxZ = chunkZ * 16 + 16 + padding;

        for (TerrainRiverSpatialIndex.Cell cell : index.cells()) {
            boolean intersects = cell.maxX() >= minX && cell.minX() <= maxX && cell.maxZ() >= minZ && cell.minZ() <= maxZ;
            if (intersects) {
                emitSpatialCell(cell, true);
            }
        }

        for (TerrainRiverNetwork.Segment segment : index.queryAabb(minX, minZ, maxX, maxZ)) {
            emitQueriedSegment(index.network(), segment);
        }
    }

    private static void emitSpatialCell(TerrainRiverSpatialIndex.Cell cell, boolean selected) {
        int count = Math.max(1, cell.segmentCount());
        float intensity = clamp01((float) Math.sqrt(Math.min(count, 16) / 16.0F));
        int fillColor = selected
                ? argb(120, 255, 230, 60)
                : spatialIndexFillColor(intensity);
        int borderColor = selected
                ? argb(255, 255, 235, 95)
                : spatialIndexBorderColor(intensity);
        float borderWidth = selected ? 0.26F : 0.18F;
        double y = cell.maxSurfaceY() + TerrainDebugOverlayState.yOffset() + 1.85D;

        Vec3 nw = new Vec3(cell.minX(), y, cell.minZ());
        Vec3 ne = new Vec3(cell.maxX(), y, cell.minZ());
        Vec3 se = new Vec3(cell.maxX(), y, cell.maxZ());
        Vec3 sw = new Vec3(cell.minX(), y, cell.maxZ());

        Gizmos.rect(nw, sw, se, ne, GizmoStyle.fill(fillColor));
        Gizmos.line(nw, ne, borderColor, borderWidth);
        Gizmos.line(ne, se, borderColor, borderWidth);
        Gizmos.line(se, sw, borderColor, borderWidth);
        Gizmos.line(sw, nw, borderColor, borderWidth);
    }

    private static int spatialIndexFillColor(float intensity) {
        if (intensity < 0.5F) {
            float t = intensity / 0.5F;
            return argb(105, lerp(35, 60, t), lerp(125, 200, t), lerp(245, 170, t));
        }
        float t = (intensity - 0.5F) / 0.5F;
        return argb(125, lerp(60, 255, t), lerp(200, 210, t), lerp(170, 45, t));
    }

    private static int spatialIndexBorderColor(float intensity) {
        if (intensity < 0.5F) {
            float t = intensity / 0.5F;
            return argb(240, lerp(40, 80, t), lerp(145, 225, t), lerp(255, 180, t));
        }
        float t = (intensity - 0.5F) / 0.5F;
        return argb(255, lerp(80, 255, t), lerp(225, 235, t), lerp(180, 60, t));
    }

    private static void emitQueriedSegment(TerrainRiverNetwork network, TerrainRiverNetwork.Segment segment) {
        int color = riverVectorColor(network, segment, TerrainDebugOverlayMode.RIVER_VECTOR_WIDTH);
        float width = Math.max(0.90F, riverVectorLineWidth(network, segment, TerrainDebugOverlayMode.RIVER_VECTOR_WIDTH));

        for (int i = 0; i < segment.points().size() - 1; i++) {
            Vec3 a = vectorPoint(segment.points().get(i));
            Vec3 b = vectorPoint(segment.points().get(i + 1));
            Gizmos.line(a, b, color, width);
        }
    }

    private static void emitRiverChunkOverlay(TerrainRiverChunkOverlay overlay, TerrainDebugOverlayMode mode) {
        for (int z = 0; z < overlay.height(); z++) {
            for (int x = 0; x < overlay.width(); x++) {
                if (!shouldRenderRiverChunkOverlayCell(overlay, x, z, mode)) {
                    continue;
                }

                int y = overlay.surfaceYAtLocal(x, z);
                emitQuad(
                        overlay.blockStartX(),
                        overlay.blockStartZ(),
                        x,
                        z,
                        x + 1,
                        z + 1,
                        y,
                        riverChunkOverlayColor(overlay, x, z, mode)
                );
            }
        }
    }

    private static boolean shouldRenderRiverChunkOverlayCell(TerrainRiverChunkOverlay overlay, int localX, int localZ, TerrainDebugOverlayMode mode) {
        return switch (mode) {
            case RIVER_CHUNK_DISTANCE -> overlay.distanceNormalizedAt(localX, localZ) < 1.0F;
            case RIVER_CHUNK_BED -> overlay.bedAt(localX, localZ) > 0.02F;
            case RIVER_CHUNK_WATER -> overlay.waterAt(localX, localZ) > 0.02F;
            case RIVER_CHUNK_BANKS -> overlay.bankAt(localX, localZ) > 0.02F;
            case RIVER_CHUNK_MATERIALS -> overlay.materialAt(localX, localZ) > 0.02F;
            case RIVER_CHUNK_VEGETATION -> overlay.vegetationAt(localX, localZ) > 0.02F;
            case RIVER_CHUNK_TERRAIN_CORRECTION -> overlay.terrainCorrectionAt(localX, localZ) > 0.02F;
            default -> false;
        };
    }

    private static int riverChunkOverlayColor(TerrainRiverChunkOverlay overlay, int localX, int localZ, TerrainDebugOverlayMode mode) {
        return switch (mode) {
            case RIVER_CHUNK_DISTANCE -> {
                float value = 1.0F - overlay.distanceNormalizedAt(localX, localZ);
                yield riverChunkDistanceColor(value);
            }
            case RIVER_CHUNK_BED -> blueIntensityColor(overlay.bedAt(localX, localZ));
            case RIVER_CHUNK_WATER -> waterOverlayColor(overlay.waterAt(localX, localZ));
            case RIVER_CHUNK_BANKS -> bankOverlayColor(overlay.bankAt(localX, localZ));
            case RIVER_CHUNK_MATERIALS -> materialOverlayColor(overlay.materialAt(localX, localZ));
            case RIVER_CHUNK_VEGETATION -> vegetationOverlayColor(overlay.vegetationAt(localX, localZ));
            case RIVER_CHUNK_TERRAIN_CORRECTION -> correctionOverlayColor(
                    normalize(overlay.terrainCorrectionAt(localX, localZ), 0.0F, Math.max(0.01F, overlay.maxTerrainCorrection()))
            );
            default -> 0x00000000;
        };
    }

    private static int riverChunkDistanceColor(float value) {
        float t = contrast(value);
        return argb(255, lerp(230, 30, t), lerp(230, 210, t), lerp(230, 255, t));
    }

    private static int blueIntensityColor(float value) {
        float t = contrast(value);
        return argb(255, lerp(40, 0, t), lerp(105, 185, t), lerp(185, 255, t));
    }

    private static int waterOverlayColor(float value) {
        float t = contrast(value);
        return argb(255, lerp(20, 0, t), lerp(130, 220, t), lerp(230, 255, t));
    }

    private static int bankOverlayColor(float value) {
        float t = contrast(value);
        return argb(255, lerp(115, 245, t), lerp(95, 190, t), lerp(45, 80, t));
    }

    private static int materialOverlayColor(float value) {
        float t = contrast(value);
        return argb(255, lerp(80, 210, t), lerp(80, 200, t), lerp(75, 170, t));
    }

    private static int vegetationOverlayColor(float value) {
        float t = contrast(value);
        return argb(255, lerp(40, 35, t), lerp(110, 235, t), lerp(45, 55, t));
    }

    private static int correctionOverlayColor(float value) {
        float t = contrast(value);
        return argb(255, lerp(240, 255, t), lerp(235, 75, t), lerp(245, 205, t));
    }

    private static void emitRiverTile(TerrainRiverTile tile, TerrainDebugOverlayMode mode) {
        int stride = TerrainDebugOverlayState.stride();
        for (int z = 0; z < tile.height(); z += stride) {
            int zEndExclusive = Math.min(z + stride, tile.height());
            for (int x = 0; x < tile.width(); x += stride) {
                int xEndExclusive = Math.min(x + stride, tile.width());
                int y = riverCellFlatY(tile, x, z, xEndExclusive, zEndExclusive);
                int sampleX = sampleCoord(x, xEndExclusive, tile.width());
                int sampleZ = sampleCoord(z, zEndExclusive, tile.height());
                emitQuad(tile.blockStartX(), tile.blockStartZ(), x, z, xEndExclusive, zEndExclusive, y, riverColor(tile, sampleX, sampleZ, mode));
            }
        }
    }

    private static void emitQuad(int blockStartX, int blockStartZ, int x0, int z0, int x1Exclusive, int z1Exclusive, int y, int color) {
        double renderY = y + TerrainDebugOverlayState.yOffset();
        double west = blockStartX + x0 - CELL_OVERLAP;
        double east = blockStartX + x1Exclusive + CELL_OVERLAP;
        double north = blockStartZ + z0 - CELL_OVERLAP;
        double south = blockStartZ + z1Exclusive + CELL_OVERLAP;

        Vec3 nw = new Vec3(west, renderY, north);
        Vec3 ne = new Vec3(east, renderY, north);
        Vec3 se = new Vec3(east, renderY, south);
        Vec3 sw = new Vec3(west, renderY, south);

        int fill = withAlpha(color, TerrainDebugOverlayState.fillAlpha());
        Gizmos.rect(nw, sw, se, ne, GizmoStyle.fill(fill));
    }

    private static int baseCellFlatY(TerrainBaseTile tile, int x0, int z0, int x1Exclusive, int z1Exclusive, TerrainDebugOverlayMode mode) {
        int x1 = Math.min(Math.max(x0, x1Exclusive - 1), tile.width() - 1);
        int z1 = Math.min(Math.max(z0, z1Exclusive - 1), tile.height() - 1);
        return Math.max(
                Math.max(baseHeightAt(tile, x0, z0, mode), baseHeightAt(tile, x1, z0, mode)),
                Math.max(baseHeightAt(tile, x1, z1, mode), baseHeightAt(tile, x0, z1, mode))
        );
    }

    private static int costCellFlatY(TerrainCostTile tile, int x0, int z0, int x1Exclusive, int z1Exclusive) {
        int x1 = Math.min(Math.max(x0, x1Exclusive - 1), tile.width() - 1);
        int z1 = Math.min(Math.max(z0, z1Exclusive - 1), tile.height() - 1);
        return Math.max(
                Math.max(tile.surfaceYAtLocal(x0, z0), tile.surfaceYAtLocal(x1, z0)),
                Math.max(tile.surfaceYAtLocal(x1, z1), tile.surfaceYAtLocal(x0, z1))
        );
    }

    private static int flowCellFlatY(TerrainFlowTile tile, int x0, int z0, int x1Exclusive, int z1Exclusive) {
        int x1 = Math.min(Math.max(x0, x1Exclusive - 1), tile.width() - 1);
        int z1 = Math.min(Math.max(z0, z1Exclusive - 1), tile.height() - 1);
        return Math.max(
                Math.max(tile.surfaceYAtLocal(x0, z0), tile.surfaceYAtLocal(x1, z0)),
                Math.max(tile.surfaceYAtLocal(x1, z1), tile.surfaceYAtLocal(x0, z1))
        );
    }

    private static int riverCellFlatY(TerrainRiverTile tile, int x0, int z0, int x1Exclusive, int z1Exclusive) {
        int x1 = Math.min(Math.max(x0, x1Exclusive - 1), tile.width() - 1);
        int z1 = Math.min(Math.max(z0, z1Exclusive - 1), tile.height() - 1);
        return Math.max(
                Math.max(tile.surfaceYAtLocal(x0, z0), tile.surfaceYAtLocal(x1, z0)),
                Math.max(tile.surfaceYAtLocal(x1, z1), tile.surfaceYAtLocal(x0, z1))
        );
    }

    private static int sampleCoord(int start, int endExclusive, int max) {
        return Math.min(start + Math.max(0, (endExclusive - start) / 2), max - 1);
    }

    private static int baseHeightAt(TerrainBaseTile tile, int localX, int localZ, TerrainDebugOverlayMode mode) {
        return switch (mode) {
            case BASE_HEIGHT -> tile.baseYAtLocal(localX, localZ);
            case GENERATED_HEIGHT, DELTA, BIOME -> tile.generatedYAtLocal(localX, localZ);
            default -> 0;
        };
    }

    private static int baseColor(TerrainBaseTile tile, int localX, int localZ, TerrainDebugOverlayMode mode) {
        return switch (mode) {
            case BASE_HEIGHT -> heightColor(tile.baseYAtLocal(localX, localZ));
            case GENERATED_HEIGHT -> heightColor(tile.generatedYAtLocal(localX, localZ));
            case DELTA -> deltaColor(tile.deltaYAtLocal(localX, localZ));
            case BIOME -> biomeColor(tile.biomeAtLocal(localX, localZ));
            default -> 0x00000000;
        };
    }

    private static int costColor(TerrainCostTile tile, int localX, int localZ, TerrainDebugOverlayMode mode) {
        float raw = switch (mode) {
            case COST_TOTAL -> tile.totalAt(localX, localZ);
            case COST_SLOPE -> tile.slopeAt(localX, localZ);
            case COST_RIDGE -> tile.ridgeAt(localX, localZ);
            case COST_VALLEY -> tile.valleyAt(localX, localZ);
            case COST_BIOME -> tile.biomeAt(localX, localZ);
            case COST_SOIL -> tile.soilAt(localX, localZ);
            case COST_FORBIDDEN -> tile.forbiddenAt(localX, localZ);
            default -> 0.0F;
        };

        float value = switch (mode) {
            case COST_TOTAL -> contrast(normalize(raw, 0.0F, 1.0F));
            case COST_SLOPE -> contrast(normalize(raw, 0.0F, 0.55F));
            case COST_RIDGE -> contrast(normalize(raw, 0.0F, 0.60F));
            case COST_VALLEY -> contrast(normalize(raw, 0.0F, 0.45F));
            case COST_BIOME -> contrast(normalize(raw, 0.0F, 0.35F));
            case COST_SOIL -> contrast(normalize(raw, 0.0F, 0.30F));
            case COST_FORBIDDEN -> raw > 0.0F ? 1.0F : 0.0F;
            default -> 0.0F;
        };

        if (mode == TerrainDebugOverlayMode.COST_FORBIDDEN && value > 0.0F) {
            return argb(255, 255, 0, 255);
        }
        if (mode == TerrainDebugOverlayMode.COST_VALLEY) {
            return valleyColor(value);
        }
        return costRampColor(value);
    }

    private static int flowColor(TerrainFlowTile tile, int localX, int localZ, TerrainDebugOverlayMode mode) {
        if (tile.isSinkAt(localX, localZ)) {
            if (mode == TerrainDebugOverlayMode.FLOW_SINKS || mode == TerrainDebugOverlayMode.FLOW_DIRECTION) {
                return argb(255, 255, 0, 255);
            }
        }

        return switch (mode) {
            case FLOW_DIRECTION -> directionColor(tile.directionAt(localX, localZ));
            case FLOW_DROP -> costRampColor(contrast(normalize(tile.dropAt(localX, localZ), 0.0F, 2.5F)));
            case FLOW_SINKS -> tile.isSinkAt(localX, localZ) ? argb(255, 255, 0, 255) : argb(255, 35, 190, 90);
            case FLOW_SELECTED_COST -> costRampColor(contrast(normalize(tile.selectedCostAt(localX, localZ), 0.0F, 1.0F)));
            case FLOW_ACCUMULATION_PREVIEW -> accumulationColor(tile.accumulationPreviewAt(localX, localZ), tile.maxAccumulationPreview());
            case FLOW_CONVERGENCE_BONUS -> convergenceColor(normalize(
                    tile.convergenceBonusAt(localX, localZ),
                    0.0F,
                    TerrainFlowConfig.CONVERGENCE_BONUS_WEIGHT
            ));
            case FLOW_CHANGED_BY_CONVERGENCE -> tile.changedByConvergenceAt(localX, localZ)
                    ? argb(255, 190, 70, 170)
                    : argb(255, 35, 95, 55);
            case FLOW_ACCUMULATION_FINAL -> accumulationColor(tile.accumulationFinalAt(localX, localZ), tile.maxAccumulationFinal());
            case FLOW_ACCUMULATION_LOG -> accumulationLogColor(tile.accumulationFinalAt(localX, localZ), tile.maxAccumulationFinal());
            case FLOW_DRAINAGE_AREA -> costRampColor(contrast(tile.drainageAreaFractionAt(localX, localZ)));
            case FLOW_RIVER_PREVIEW -> tile.isRiverPreviewAt(localX, localZ)
                    ? riverPreviewColor(tile.accumulationFinalAt(localX, localZ), tile.maxAccumulationFinal())
                    : argb(255, 25, 35, 42);
            case FLOW_SINK_ACCUMULATION -> tile.isSinkAt(localX, localZ)
                    ? accumulationColor(tile.accumulationFinalAt(localX, localZ), tile.maxAccumulationFinal())
                    : argb(255, 35, 120, 70);
            default -> 0x00000000;
        };
    }

    private static int riverColor(TerrainRiverTile tile, int localX, int localZ, TerrainDebugOverlayMode mode) {
        boolean river = tile.isRiverAt(localX, localZ);
        if (!river) {
            return argb(255, 22, 30, 36);
        }

        return switch (mode) {
            case RIVER_CELLS -> riverPreviewColor(tile.accumulationAt(localX, localZ), tile.maxAccumulation());
            case RIVER_SOURCES -> tile.isSourceAt(localX, localZ)
                    ? argb(255, 80, 255, 255)
                    : riverMutedColor(tile.logStrengthAt(localX, localZ));
            case RIVER_CONFLUENCES -> tile.isConfluenceAt(localX, localZ)
                    ? argb(255, 255, 170, 30)
                    : riverMutedColor(tile.logStrengthAt(localX, localZ));
            case RIVER_OUTLETS -> tile.isOutletAt(localX, localZ)
                    ? argb(255, 255, 80, 235)
                    : riverMutedColor(tile.logStrengthAt(localX, localZ));
            case RIVER_WIDTH_PREVIEW -> riverWidthColor(tile.widthBlocksAt(localX, localZ));
            default -> 0x00000000;
        };
    }

    private static int accumulationColor(float accumulation, float maxAccumulation) {
        float value = logNormalizedAccumulation(accumulation, maxAccumulation);
        return costRampColor(contrast(value));
    }

    private static int accumulationLogColor(float accumulation, float maxAccumulation) {
        float value = logNormalizedAccumulation(accumulation, maxAccumulation);
        int r = lerp(25, 40, value);
        int g = lerp(40, 210, value);
        int b = lerp(70, 255, value);
        return argb(255, r, g, b);
    }

    private static int riverPreviewColor(float accumulation, float maxAccumulation) {
        float value = contrast(logNormalizedAccumulation(accumulation, maxAccumulation));
        int r = lerp(20, 0, value);
        int g = lerp(130, 230, value);
        int b = lerp(220, 255, value);
        return argb(255, r, g, b);
    }

    private static int riverMutedColor(float value) {
        float t = contrast(value);
        int r = lerp(20, 40, t);
        int g = lerp(95, 150, t);
        int b = lerp(130, 220, t);
        return argb(255, r, g, b);
    }

    private static int riverWidthColor(float widthBlocks) {
        return riverWidthVectorColor(widthBlocks);
    }

    private static int riverWidthVectorColor(float widthBlocks) {
        float value = widthVisualNormalized(widthBlocks);
        if (value < 0.33F) {
            float t = value / 0.33F;
            return argb(255, lerp(20, 25, t), lerp(90, 175, t), lerp(225, 255, t));
        }
        if (value < 0.66F) {
            float t = (value - 0.33F) / 0.33F;
            return argb(255, lerp(25, 245, t), lerp(175, 220, t), lerp(255, 55, t));
        }
        float t = (value - 0.66F) / 0.34F;
        return argb(255, lerp(245, 255, t), lerp(220, 115, t), lerp(55, 25, t));
    }

    private static float widthVisualNormalized(float widthBlocks) {
        float base = normalize(widthBlocks, TerrainRiverConfig.MIN_WIDTH_BLOCKS, TerrainRiverConfig.MAX_WIDTH_BLOCKS);
        return (float) Math.sqrt(clamp01(base));
    }

    private static int depthColor(float depth, float maxDepth) {
        float value = maxDepth <= 0.0F ? 0.0F : contrast(clamp01(depth / maxDepth));
        int r = lerp(45, 145, value);
        int g = lerp(120, 45, value);
        int b = lerp(255, 255, value);
        return argb(255, r, g, b);
    }

    private static float logNormalizedAccumulation(float accumulation, float maxAccumulation) {
        float denominator = (float) Math.log1p(Math.max(1.0F, maxAccumulation));
        return denominator <= 0.0F ? 0.0F : clamp01((float) (Math.log1p(Math.max(0.0F, accumulation)) / denominator));
    }

    private static int convergenceColor(float normalizedBonus) {
        float value = contrast(normalizedBonus);
        int r = lerp(235, 60, value);
        int g = lerp(235, 135, value);
        int b = lerp(235, 255, value);
        return argb(255, r, g, b);
    }

    private static int directionColor(byte direction) {
        return switch (direction) {
            case 0 -> argb(255, 35, 95, 240);
            case 1 -> argb(255, 35, 180, 240);
            case 2 -> argb(255, 35, 220, 145);
            case 3 -> argb(255, 145, 220, 35);
            case 4 -> argb(255, 235, 220, 35);
            case 5 -> argb(255, 235, 145, 35);
            case 6 -> argb(255, 235, 65, 35);
            case 7 -> argb(255, 170, 55, 220);
            default -> argb(255, 255, 0, 255);
        };
    }

    private static int costRampColor(float value) {
        float t = clamp01(value);
        int r;
        int g;
        int b;
        if (t < 0.33F) {
            float k = t / 0.33F;
            r = lerp(35, 65, k);
            g = lerp(115, 220, k);
            b = lerp(230, 115, k);
        } else if (t < 0.66F) {
            float k = (t - 0.33F) / 0.33F;
            r = lerp(65, 235, k);
            g = lerp(220, 220, k);
            b = lerp(115, 45, k);
        } else {
            float k = (t - 0.66F) / 0.34F;
            r = lerp(235, 225, k);
            g = lerp(220, 45, k);
            b = lerp(45, 45, k);
        }
        return argb(255, r, g, b);
    }

    private static int valleyColor(float value) {
        float t = clamp01(value);
        int r = lerp(235, 25, t);
        int g = lerp(235, 190, t);
        int b = lerp(235, 255, t);
        return argb(255, r, g, b);
    }

    private static int heightColor(int y) {
        int normalized = Mth.clamp((y - 32) * 255 / 160, 0, 255);
        int r;
        int g;
        int b;
        if (normalized < 85) {
            float t = normalized / 85.0F;
            r = lerp(20, 80, t);
            g = lerp(110, 230, t);
            b = lerp(230, 80, t);
        } else if (normalized < 170) {
            float t = (normalized - 85) / 85.0F;
            r = lerp(80, 230, t);
            g = lerp(230, 215, t);
            b = lerp(80, 50, t);
        } else {
            float t = (normalized - 170) / 85.0F;
            r = lerp(230, 220, t);
            g = lerp(215, 55, t);
            b = lerp(50, 45, t);
        }
        return argb(255, r, g, b);
    }

    private static int deltaColor(int deltaY) {
        int magnitude = Mth.clamp(Math.abs(deltaY) * 24, 0, 255);
        if (deltaY > 0) {
            return argb(255, 150 + magnitude / 2, 45, 45);
        }
        if (deltaY < 0) {
            return argb(255, 45, 90, 150 + magnitude / 2);
        }
        return argb(255, 210, 210, 210);
    }

    private static int biomeColor(short biomeId) {
        int hash = biomeId * 1103515245 + 12345;
        int r = 64 + ((hash >> 16) & 127);
        int g = 64 + ((hash >> 8) & 127);
        int b = 64 + (hash & 127);
        return argb(255, r, g, b);
    }

    private static float normalize(float value, float min, float max) {
        if (max == min) {
            return value <= min ? 0.0F : 1.0F;
        }
        return clamp01((value - min) / (max - min));
    }

    private static float contrast(float value) {
        return (float) Math.pow(clamp01(value), 0.62D);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static int lerp(int a, int b, float t) {
        return Mth.clamp(Math.round(a + (b - a) * t), 0, 255);
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 255) << 24);
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
}
