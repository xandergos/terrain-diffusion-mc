package com.github.xandergos.terraindiffusionmc.client.basin;

import com.github.xandergos.terraindiffusionmc.basin.IrrigationBasinBuilder;
import com.github.xandergos.terraindiffusionmc.basin.IrrigationBasinMesh;
import com.github.xandergos.terraindiffusionmc.basin.LaplacianBasinSegmenter;
import com.github.xandergos.terraindiffusionmc.world.HeightConverter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;

/**
 * Draws the irrigation basin mesh (IBM) overlay into the world.
 *
 * <p>Meshes are pulled from {@link IrrigationBasinBuilder};
 * tiles still being built return {@code null} and are skipped so the overlay pops in tile-by-tile
 * as the segmentation thread completes them.
 *
 * <p>Two logical layers compose the overlay, both backed by {@link IrrigationBasinRenderLayers#BASIN_OVERLAY}:
 * <ul>
 *   <li>basin fill : semi-transparent quad per basin cell colored by id</li>
 *   <li>ridge outline : thin draped quads along each ridge cell's bounding edges. Also a WIP</li>
 * </ul>
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class IrrigationBasinRenderer {

    /** Vertical offset above the surface to avoid z-fighting with terrain blocks. */
    private static final float SURFACE_LIFT = 1.55f;

    /** Half-thickness of a ridge "line" quad in blocks. */
    private static final float LINE_HALF_WIDTH = 0.15f;

    /** Alpha used for basin fill quads. */
    private static final int FILL_ALPHA = 0x99; // ~60%

    /** ARGB color for ridge cells. */
    private static final int RIDGE_ARGB = 0xFF202020;

    private IrrigationBasinRenderer() {
    }

    /**
     * Vertex coordinates are emitted in camera-relative space matching Minecraft's
     * convention for everything drawn through the immediate provider.
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Camera camera) {
        if (!IrrigationBasinState.overlayEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        double cameraX = camera.getCameraPos().x;
        double cameraY = camera.getCameraPos().y;
        double cameraZ = camera.getCameraPos().z;

        int playerBlockI = (int) Math.floor(cameraEntity.getZ());
        int playerBlockJ = (int) Math.floor(cameraEntity.getX());
        int playerTileI = Math.floorDiv(playerBlockI, IrrigationBasinBuilder.TILE_SIZE);
        int playerTileJ = Math.floorDiv(playerBlockJ, IrrigationBasinBuilder.TILE_SIZE);
        int radius = IrrigationBasinState.renderRadiusTiles;

        Matrix4f modelViewMatrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = immediate.getBuffer(IrrigationBasinRenderLayers.BASIN_OVERLAY);

        for (int tileI = playerTileI - radius; tileI <= playerTileI + radius; tileI++) {
            for (int tileJ = playerTileJ - radius; tileJ <= playerTileJ + radius; tileJ++) {
                int originBlockI = tileI * IrrigationBasinBuilder.TILE_SIZE;
                int originBlockJ = tileJ * IrrigationBasinBuilder.TILE_SIZE;
                IrrigationBasinMesh mesh = IrrigationBasinBuilder.getOrSchedule(originBlockI, originBlockJ);
                if (mesh != null) drawMesh(mesh, modelViewMatrix, buffer, cameraX, cameraY, cameraZ);
            }
        }

        immediate.draw(IrrigationBasinRenderLayers.BASIN_OVERLAY);
    }

    private static void drawMesh(IrrigationBasinMesh mesh, Matrix4f modelViewMatrix, VertexConsumer buffer,
                                 double cameraX, double cameraY, double cameraZ) {
        if (IrrigationBasinState.showRidges) {
            drawRidges(mesh, modelViewMatrix, buffer, cameraX, cameraY, cameraZ);
        }
        if (IrrigationBasinState.showBasinFill) {
            drawBasinFill(mesh, modelViewMatrix, buffer, cameraX, cameraY, cameraZ);
        }
    }

    /**
     * Emits four thin quads around each ridge cell forming a square outline draped on the surface. WIP also lol
     */
    private static void drawRidges(IrrigationBasinMesh mesh, Matrix4f modelViewMatrix, VertexConsumer buffer,
                                   double cameraX, double cameraY, double cameraZ) {
        for (int row = 0; row < mesh.height; row++) {
            for (int col = 0; col < mesh.width; col++) {
                if (!mesh.isRidge(row, col)) continue;

                float worldX = (float) (mesh.originBlockJ + col - cameraX);
                float worldZ = (float) (mesh.originBlockI + row - cameraZ);
                float worldY = (float) (HeightConverter.convertToMinecraftHeight(mesh.heightmap[row][col]) + SURFACE_LIFT + 0.02f - cameraY);

                emitHorizontalSegment(buffer, modelViewMatrix, worldX, worldY, worldZ, worldX + 1f, worldY, worldZ, RIDGE_ARGB, true);
                emitHorizontalSegment(buffer, modelViewMatrix, worldX + 1f, worldY, worldZ, worldX + 1f, worldY, worldZ + 1f, RIDGE_ARGB, false);
                emitHorizontalSegment(buffer, modelViewMatrix, worldX, worldY, worldZ + 1f, worldX + 1f, worldY, worldZ + 1f, RIDGE_ARGB, true);
                emitHorizontalSegment(buffer, modelViewMatrix, worldX, worldY, worldZ, worldX, worldY, worldZ + 1f, RIDGE_ARGB, false);
            }
        }
    }

    /**
     * Emits one semi-transparent quad per basin cell colored deterministically by basin id.
     * Each quad is draped on the heightmap surface.
     */
    private static void drawBasinFill(IrrigationBasinMesh mesh, Matrix4f modelViewMatrix, VertexConsumer buffer,
                                      double cameraX, double cameraY, double cameraZ) {
        for (int row = 0; row < mesh.height; row++) {
            for (int col = 0; col < mesh.width; col++) {
                int label = mesh.labelAt(row, col);
                if (label == LaplacianBasinSegmenter.LABEL_RIDGE) continue;

                float worldX = (float) (mesh.originBlockJ + col - cameraX);
                float worldZ = (float) (mesh.originBlockI + row - cameraZ);
                float worldY = (float) (HeightConverter.convertToMinecraftHeight(mesh.heightmap[row][col]) + SURFACE_LIFT - cameraY);
                int color = basinFillColor(label);

                buffer.vertex(modelViewMatrix, worldX,      worldY, worldZ     ).color(color);
                buffer.vertex(modelViewMatrix, worldX,      worldY, worldZ + 1f).color(color);
                buffer.vertex(modelViewMatrix, worldX + 1f, worldY, worldZ + 1f).color(color);
                buffer.vertex(modelViewMatrix, worldX + 1f, worldY, worldZ     ).color(color);
            }
        }
    }

    /**
     * Emits a horizontal "line" quad between two endpoints at a fixed Y.
     * The quad is widened along the perpendicular axis : Z when {@code alongX} is {@code true}, X otherwise.
     */
    private static void emitHorizontalSegment(VertexConsumer buffer, Matrix4f modelViewMatrix,
                                              float x1, float y1, float z1,
                                              float x2, float y2, float z2,
                                              int color, boolean alongX) {
        if (alongX) {
            buffer.vertex(modelViewMatrix, x1, y1, z1 - LINE_HALF_WIDTH).color(color);
            buffer.vertex(modelViewMatrix, x1, y1, z1 + LINE_HALF_WIDTH).color(color);
            buffer.vertex(modelViewMatrix, x2, y2, z2 + LINE_HALF_WIDTH).color(color);
            buffer.vertex(modelViewMatrix, x2, y2, z2 - LINE_HALF_WIDTH).color(color);
        } else {
            buffer.vertex(modelViewMatrix, x1 - LINE_HALF_WIDTH, y1, z1).color(color);
            buffer.vertex(modelViewMatrix, x2 - LINE_HALF_WIDTH, y2, z2).color(color);
            buffer.vertex(modelViewMatrix, x2 + LINE_HALF_WIDTH, y2, z2).color(color);
            buffer.vertex(modelViewMatrix, x1 + LINE_HALF_WIDTH, y1, z1).color(color);
        }
    }

    /**
     * Maps a basin id to a stable ARGB color using a golden-ratio hue rotation.
     */
    private static int basinFillColor(int basinId) {
        float hue = (basinId * 0.61803398875f) % 1f;
        int rgb = hsvToRgb(hue, 0.85f, 0.75f);
        return (0xFF << 24) | rgb;
    }

    // Just read the variable name at some point (yes it's a joke but this repo has comment style standards so I try my best to explain things when they get really, really complex)
    private static int hsvToRgb(float hue, float saturation, float value) {
        int sector = (int) Math.floor(hue * 6f);
        float fraction = hue * 6f - sector;
        float p = value * (1f - saturation);
        float q = value * (1f - fraction * saturation);
        float t = value * (1f - (1f - fraction) * saturation);
        float r, g, b;
        switch (sector % 6) {
            case 0 -> { r = value; g = t;     b = p;     }
            case 1 -> { r = q;     g = value; b = p;     }
            case 2 -> { r = p;     g = value; b = t;     }
            case 3 -> { r = p;     g = q;     b = value; }
            case 4 -> { r = t;     g = p;     b = value; }
            default -> { r = value; g = p;    b = q;     }
        }
        int ri = Math.round(r * 255f);
        int gi = Math.round(g * 255f);
        int bi = Math.round(b * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }
}