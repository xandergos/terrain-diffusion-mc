package com.github.xandergos.terraindiffusionmc.client.debug;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.debug.river.TerrainRiverConfig;
import com.github.xandergos.terraindiffusionmc.debug.river.vector.TerrainRiverNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class TerrainDebugOverlayRendererCore {
    private TerrainDebugOverlayRendererCore() {
    }

    public static void render(LevelRenderer levelRenderer) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || levelRenderer == null) {
            return;
        }

        TerrainDebugOverlayMode mode = TerrainDebugOverlayState.mode();
        if (mode == TerrainDebugOverlayMode.OFF) {
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

                    TerrainDebugTileClientCache.requestRiverVector(blockStartZ, blockStartX, tileSize, tileSize);
                    TerrainRiverNetwork network = TerrainDebugTileClientCache.getRiverVectorIfReady(blockStartZ, blockStartX, tileSize, tileSize);
                    if (network != null) {
                        emitRiverTraceWidth(network);
                    }
                }
            }
        }
    }

    private static void emitRiverTraceWidth(TerrainRiverNetwork network) {
        for (TerrainRiverNetwork.Segment segment : network.segments()) {
            if (segment.points().size() < 2) {
                continue;
            }

            int color = riverWidthVectorColor(segment.maxWidthBlocks());
            float width = riverTraceLineWidth(segment);
            for (int i = 0; i < segment.points().size() - 1; i++) {
                Vec3 a = vectorPoint(segment.points().get(i));
                Vec3 b = vectorPoint(segment.points().get(i + 1));
                Gizmos.line(a, b, color, width);
            }
        }
    }

    private static Vec3 vectorPoint(TerrainRiverNetwork.Point point) {
        return new Vec3(
                point.worldX(),
                point.surfaceY() + TerrainDebugOverlayState.yOffset() + 0.78D,
                point.worldZ()
        );
    }

    private static float riverTraceLineWidth(TerrainRiverNetwork.Segment segment) {
        float n = widthVisualNormalized(segment.maxWidthBlocks());
        return Mth.clamp(0.42F + n * 2.75F, 0.42F, 3.10F);
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

    private static float normalize(float value, float min, float max) {
        if (max == min) {
            return value <= min ? 0.0F : 1.0F;
        }
        return clamp01((value - min) / (max - min));
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

    private static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
}
