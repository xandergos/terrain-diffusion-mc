package com.github.xandergos.terraindiffusionmc.client.debug;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.debug.TerrainBaseTile;
import com.github.xandergos.terraindiffusionmc.debug.cost.TerrainCostTile;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowConfig;
import com.github.xandergos.terraindiffusionmc.debug.flow.TerrainFlowTile;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverConfig;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverTile;
import com.github.xandergos.terraindiffusionmc.debug.river.overlay.TerrainRiverChunkOverlay;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverNetwork;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverSpatialIndex;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverVectorConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

public final class TerrainDebugOverlayRenderer {
    private static final double CELL_OVERLAP = 0.035D;

    private TerrainDebugOverlayRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(TerrainDebugOverlayRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        TerrainDebugOverlayMode mode = TerrainDebugOverlayState.mode();
        if (mode == TerrainDebugOverlayMode.OFF) {
            return;
        }

        WorldRenderer worldRenderer = context.worldRenderer();
        if (worldRenderer == null) {
            return;
        }

        int tileSize = TerrainDiffusionConfig.tileSize();
        int playerBlockX = MathHelper.floor(client.player.getX());
        int playerBlockZ = MathHelper.floor(client.player.getZ());
        int centerTileX = Math.floorDiv(playerBlockX, tileSize);
        int centerTileZ = Math.floorDiv(playerBlockZ, tileSize);
        int radius = TerrainDebugOverlayState.radiusTiles();

        try (GizmoDrawing.CollectorScope ignored = worldRenderer.startDrawingGizmos()) {
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
            Vec3d a = vectorPoint(segment.points().get(i));
            Vec3d b = vectorPoint(segment.points().get(i + 1));
            GizmoDrawing.line(a, b, color, width);
        }
    }

    private static void emitVectorNode(TerrainRiverNetwork.Node node) {
        Vec3d p = vectorNodePoint(node);
        GizmoDrawing.point(p, vectorNodeColor(node.type()), vectorNodeSize(node.type()));
    }

    private static Vec3d vectorPoint(TerrainRiverNetwork.Point point) {
        return new Vec3d(
                point.worldX(),
                point.surfaceY() + TerrainDebugOverlayState.yOffset() + 0.78D,
                point.worldZ()
        );
    }

    private static Vec3d vectorNodePoint(TerrainRiverNetwork.Node node) {
        return new Vec3d(
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
                yield MathHelper.clamp(0.34F + n * 2.55F, 0.34F, 2.90F);
            }
            case RIVER_VECTOR_FLOW -> {
                float n = contrast(logNormalizedAccumulation(segment.maxAccumulation(), network.maxAccumulation()));
                yield MathHelper.clamp(0.34F + n * 2.25F, 0.34F, 2.55F);
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

        Vec3d nw = new Vec3d(cell.minX(), y, cell.minZ());
        Vec3d ne = new Vec3d(cell.maxX(), y, cell.minZ());
        Vec3d se = new Vec3d(cell.maxX(), y, cell.maxZ());
        Vec3d sw = new Vec3d(cell.minX(), y, cell.maxZ());

        GizmoDrawing.quad(nw, sw, se, ne, DrawStyle.filled(fillColor));
        GizmoDrawing.line(nw, ne, borderColor, borderWidth);
        GizmoDrawing.line(ne, se, borderColor, borderWidth);
        GizmoDrawing.line(se, sw, borderColor, borderWidth);
        GizmoDrawing.line(sw, nw, borderColor, borderWidth);
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
            Vec3d a = vectorPoint(segment.points().get(i));
            Vec3d b = vectorPoint(segment.points().get(i + 1));
            GizmoDrawing.line(a, b, color, width);
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

        Vec3d nw = new Vec3d(west, renderY, north);
        Vec3d ne = new Vec3d(east, renderY, north);
        Vec3d se = new Vec3d(east, renderY, south);
        Vec3d sw = new Vec3d(west, renderY, south);

        int fill = withAlpha(color, TerrainDebugOverlayState.fillAlpha());
        GizmoDrawing.quad(nw, sw, se, ne, DrawStyle.filled(fill));
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
        int normalized = MathHelper.clamp((y - 32) * 255 / 160, 0, 255);
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
        int magnitude = MathHelper.clamp(Math.abs(deltaY) * 24, 0, 255);
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
        return MathHelper.clamp(Math.round(a + (b - a) * t), 0, 255);
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 255) << 24);
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
}
