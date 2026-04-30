package com.github.xandergos.terraindiffusionmc.client.hydro;

import com.github.xandergos.terraindiffusionmc.hydro.HydrologicalFeature;
import com.github.xandergos.terraindiffusionmc.hydro.HydrologyBuilder;
import com.github.xandergos.terraindiffusionmc.hydro.HydrologyConstants;
import com.github.xandergos.terraindiffusionmc.hydro.HydrologyTile;
import com.github.xandergos.terraindiffusionmc.world.HeightConverter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;

/**
 * Draws the hydrology debug overlay : rivers as continuous polylines whose width grows with the
 * upstream contributing area, plus optional debug layers showing per-cell flow direction arrows
 * and pit-filled cells.
 *
 * <p>Tiles are pulled from {@link HydrologyBuilder} ; tiles still being built return {@code null}
 * and are skipped so the overlay pops in tile-by-tile as the worker thread completes them.
 *
 * <p>Three logical layers compose the overlay ; all toggleable from the settings screen :
 * <ul>
 *   <li><b>rivers</b> Every cell classified as a stream / river / trunk river / river mouth
 *       (see {@link com.github.xandergos.terraindiffusionmc.hydro.HydrologyClassifier}) is drawn :
 *       streams in cyan, standard rivers in water-blue, trunk rivers in red, river mouths in
 *       lime. Width on the segments scales as {@code log(accumulation)}.</li>
 *   <li><b>flow arrows</b> For every land cell a small triangle pointing toward its D8
 *       downstream neighbor. Useful to validate the flow direction field cell by cell.</li>
 *   <li><b>filled delta</b> Every cell whose pit-filled elevation differs from the raw
 *       elevation is highlighted in red. Reveals where dams formed during pit-filling.</li>
 * </ul>
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyRenderer {

    /** Vertical offset above the surface to avoid z-fighting with terrain blocks. */
    private static final float SURFACE_LIFT = 1.55f;

    /** Half-width per unit log(accumulation) when drawing river segments. */
    private static final float RIVER_WIDTH_PER_LOG = 0.18f;

    /** Hard ceiling on river half-width (blocks) so the biggest rivers don't drown the whole screen. */
    private static final float RIVER_MAX_HALF_WIDTH = 3.5f;

    /** Half-length of a flow direction arrow (blocks). */
    private static final float ARROW_HALF_LENGTH = 0.4f;

    /** Half-thickness of a flow direction arrow (blocks). */
    private static final float ARROW_HALF_THICKNESS = 0.05f;

    private HydrologyRenderer() {}

    /**
     * Top-level entry point called once per frame from the world render hook.
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Camera camera) {
        if (!HydrologyState.overlayEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        double cameraX = camera.getCameraPos().x;
        double cameraY = camera.getCameraPos().y;
        double cameraZ = camera.getCameraPos().z;

        int playerBlockI = (int) Math.floor(cameraEntity.getZ());
        int playerBlockJ = (int) Math.floor(cameraEntity.getX());
        int playerTileI = Math.floorDiv(playerBlockI, HydrologyBuilder.TILE_SIZE);
        int playerTileJ = Math.floorDiv(playerBlockJ, HydrologyBuilder.TILE_SIZE);
        int radiusTiles = Math.max(1, HydrologyState.renderRadius / HydrologyBuilder.TILE_SIZE);

        Matrix4f modelView = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = immediate.getBuffer(HydrologyRenderLayers.HYDRO_OVERLAY);

        for (int tileI = playerTileI - radiusTiles; tileI <= playerTileI + radiusTiles; tileI++) {
            for (int tileJ = playerTileJ - radiusTiles; tileJ <= playerTileJ + radiusTiles; tileJ++) {
                int originBlockI = tileI * HydrologyBuilder.TILE_SIZE;
                int originBlockJ = tileJ * HydrologyBuilder.TILE_SIZE;
                HydrologyTile tile = HydrologyBuilder.getOrSchedule(originBlockI, originBlockJ);
                if (tile != null) drawTile(tile, modelView, buffer, cameraX, cameraY, cameraZ);
            }
        }

        immediate.draw(HydrologyRenderLayers.HYDRO_OVERLAY);
    }

    private static void drawTile(HydrologyTile tile, Matrix4f modelView, VertexConsumer buffer,
                                 double cameraX, double cameraY, double cameraZ) {
        if (HydrologyState.showFilledDelta) {
            drawFilledDelta(tile, modelView, buffer, cameraX, cameraY, cameraZ);
        }
        if (HydrologyState.showFlowArrows) {
            drawFlowArrows(tile, modelView, buffer, cameraX, cameraY, cameraZ);
        }
        if (HydrologyState.showRivers) {
            drawRivers(tile, modelView, buffer, cameraX, cameraY, cameraZ);
        }
    }

    /**
     * Highlights every cell whose pit-filled elevation is strictly higher than the raw heightmap.
     */
    private static void drawFilledDelta(HydrologyTile tile, Matrix4f modelView, VertexConsumer buffer,
                                        double cameraX, double cameraY, double cameraZ) {
        final int color = 0x55FF3030; // semi-transparent red
        for (int row = 0; row < tile.height; row++) {
            for (int col = 0; col < tile.width; col++) {
                int raw = tile.heightmap[row][col];
                int filled = tile.filledHeightmap[row][col];
                if (filled <= raw) continue;
                emitCellQuad(buffer, modelView, tile, row, col, color, cameraX, cameraY, cameraZ);
            }
        }
    }

    /**
     * Draws a thin triangle per land cell pointing toward its D8 downstream neighbor.
     */
    private static void drawFlowArrows(HydrologyTile tile, Matrix4f modelView, VertexConsumer buffer,
                                       double cameraX, double cameraY, double cameraZ) {
        final int color = 0xCCFFFFFF;
        for (int row = 0; row < tile.height; row++) {
            for (int col = 0; col < tile.width; col++) {
                byte dir = tile.flowDirection[row * tile.width + col];
                if (dir == HydrologyConstants.FLOW_DIR_SEA) continue;

                float dx = HydrologyConstants.D8_DCOL[dir];
                float dz = HydrologyConstants.D8_DROW[dir];
                float length = (float) Math.sqrt(dx * dx + dz * dz);
                dx /= length;
                dz /= length;

                float[] centre = surfacePoint(tile, row, col, cameraX, cameraY, cameraZ);
                float tipX = centre[0] + dx * ARROW_HALF_LENGTH;
                float tipZ = centre[2] + dz * ARROW_HALF_LENGTH;
                float tailX = centre[0] - dx * ARROW_HALF_LENGTH;
                float tailZ = centre[2] - dz * ARROW_HALF_LENGTH;
                float perpX = -dz * ARROW_HALF_THICKNESS;
                float perpZ =  dx * ARROW_HALF_THICKNESS;
                float y = centre[1];

                buffer.vertex(modelView, tailX - perpX, y, tailZ - perpZ).color(color);
                buffer.vertex(modelView, tailX + perpX, y, tailZ + perpZ).color(color);
                buffer.vertex(modelView, tipX  + perpX, y, tipZ  + perpZ).color(color);
                buffer.vertex(modelView, tipX  - perpX, y, tipZ  - perpZ).color(color);
            }
        }
    }

    /**
     * Draws every cell whose {@link HydrologicalFeature} is a watercourse or river mouth.
     *
     * <p>Streams, rivers and trunk rivers are drawn as quad segments from the cell to its D8
     * downstream neighbor with width scaling as {@code log(accumulation)} (capped at
     * {@link #RIVER_MAX_HALF_WIDTH}) and color picked from {@link FeatureColors}. River-mouth
     * cells are sea cells inside a river's discharge plume — they're painted as a full lime
     * cell quad to highlight the river's outlet area.
     */
    private static void drawRivers(HydrologyTile tile, Matrix4f modelView, VertexConsumer buffer,
                                   double cameraX, double cameraY, double cameraZ) {
        for (int row = 0; row < tile.height; row++) {
            for (int col = 0; col < tile.width; col++) {
                int idx = row * tile.width + col;
                HydrologicalFeature feature = HydrologicalFeature.fromOrdinal(tile.features[idx]);

                switch (feature) {
                    case STREAM, RIVER, TRUNK_RIVER -> {
                        byte dir = tile.flowDirection[idx];
                        if (dir == HydrologyConstants.FLOW_DIR_SEA) continue;
                        int neighbourRow = row + HydrologyConstants.D8_DROW[dir];
                        int neighbourCol = col + HydrologyConstants.D8_DCOL[dir];
                        if (neighbourRow < 0 || neighbourRow >= tile.height
                                || neighbourCol < 0 || neighbourCol >= tile.width) continue;

                        int accum = tile.flowAccumulation[idx];
                        float halfWidth = (float) Math.min(
                                RIVER_MAX_HALF_WIDTH,
                                Math.log(Math.max(2, accum)) * RIVER_WIDTH_PER_LOG);
                        int color = FeatureColors.colorFor(feature);

                        float[] from = surfacePoint(tile, row, col, cameraX, cameraY, cameraZ);
                        float[] to = surfacePoint(tile, neighbourRow, neighbourCol, cameraX, cameraY, cameraZ);
                        emitFlowSegment(buffer, modelView, from, to, halfWidth, color);
                    }
                    case RIVER_MOUTH -> {
                        /**
                         * River mouths sit on sea cells whose heightmap is the ocean floor.
                         * Render the quad just above the sea surface so it stays visible from
                         * above instead of being buried under the water. Usually river flow
                         * continue underwater.
                         */
                        float worldY = (float) (HydrologyConstants.SEA_LEVEL_Y + 1.5f - cameraY);
                        emitCellQuadAtY(buffer, modelView, tile, row, col,
                                FeatureColors.colorFor(feature),
                                worldY, cameraX, cameraZ);
                    }
                    default -> {
                        // nothing to draw
                    }
                }
            }
        }
    }

    /**
     * Returns the camera-relative world position at the surface of a cell, lifted slightly above
     * the terrain blocks to prevent z-fighting.
     */
    private static float[] surfacePoint(HydrologyTile tile, int row, int col,
                                        double cameraX, double cameraY, double cameraZ) {
        float worldX = (float) (tile.originBlockJ + col + 0.5f - cameraX);
        float worldZ = (float) (tile.originBlockI + row + 0.5f - cameraZ);
        float worldY = (float) (HeightConverter.convertToMinecraftHeight(tile.heightmap[row][col])
                + SURFACE_LIFT - cameraY);
        return new float[]{worldX, worldY, worldZ};
    }

    /**
     * Emits a single horizontal cell-sized quad at the cell's surface elevation.
     */
    private static void emitCellQuad(VertexConsumer buffer, Matrix4f modelView, HydrologyTile tile,
                                     int row, int col, int color,
                                     double cameraX, double cameraY, double cameraZ) {
        float worldY = (float) (HeightConverter.convertToMinecraftHeight(tile.heightmap[row][col])
                + SURFACE_LIFT - cameraY);
        emitCellQuadAtY(buffer, modelView, tile, row, col, color, worldY, cameraX, cameraZ);
    }

    /**
     * Emits a cell-sized quad at the given camera-relative {@code worldY}. Used by river mouths,
     * which need to sit just above the sea surface ({@code Y = 64}) rather than on the ocean
     * floor.
     */
    private static void emitCellQuadAtY(VertexConsumer buffer, Matrix4f modelView, HydrologyTile tile,
                                        int row, int col, int color, float worldY,
                                        double cameraX, double cameraZ) {
        float worldX = (float) (tile.originBlockJ + col - cameraX);
        float worldZ = (float) (tile.originBlockI + row - cameraZ);
        buffer.vertex(modelView, worldX,      worldY, worldZ     ).color(color);
        buffer.vertex(modelView, worldX,      worldY, worldZ + 1f).color(color);
        buffer.vertex(modelView, worldX + 1f, worldY, worldZ + 1f).color(color);
        buffer.vertex(modelView, worldX + 1f, worldY, worldZ     ).color(color);
    }

    /**
     * Emits a thick horizontal quad between two points, widened perpendicular to the segment.
     */
    private static void emitFlowSegment(VertexConsumer buffer, Matrix4f modelView,
                                        float[] from, float[] to, float halfWidth, int color) {
        float dx = to[0] - from[0];
        float dz = to[2] - from[2];
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-4f) return;
        float perpX = -dz / length * halfWidth;
        float perpZ =  dx / length * halfWidth;

        buffer.vertex(modelView, from[0] - perpX, from[1], from[2] - perpZ).color(color);
        buffer.vertex(modelView, from[0] + perpX, from[1], from[2] + perpZ).color(color);
        buffer.vertex(modelView, to[0]   + perpX, to[1],   to[2]   + perpZ).color(color);
        buffer.vertex(modelView, to[0]   - perpX, to[1],   to[2]   - perpZ).color(color);
    }
}