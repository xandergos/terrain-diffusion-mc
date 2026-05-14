package com.github.xandergos.terraindiffusionmc.client.debug;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public final class TerrainDebugOverlayRenderer {
    private TerrainDebugOverlayRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(TerrainDebugOverlayRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        TerrainDebugOverlayRendererCore.render(context.worldRenderer());
    }
}
