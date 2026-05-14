package com.github.xandergos.terraindiffusionmc.client.debug;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class TerrainDebugOverlayRenderer {
    private TerrainDebugOverlayRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TerrainDebugOverlayRenderer::render);
    }

    private static void render(RenderLevelStageEvent.AfterEntities event) {
        TerrainDebugOverlayRendererCore.render(event.getLevelRenderer());
    }
}
