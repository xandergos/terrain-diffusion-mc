package com.github.xandergos.terraindiffusionmc.client.basin;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

/**
 * Custom render layers used by {@link IrrigationBasinRenderer}.
 *
 * <p>A single {@link RenderPipelines#DEBUG_QUADS} pipeline (vertex format {@code POSITION_COLOR})
 * is used for every primitive : basin fill and ridges. Ridges are emitted as thin draped quads
 * rather than real lines because the {@code LINES} pipeline expects {@code POSITION_COLOR_NORMAL} ;
 * which would force a normal on every vertex. A single quad-based layer simplifies vertex emission
 * and future visual customisation. WIP or gonna be deleted !
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class IrrigationBasinRenderLayers {

    /** Translucent quad layer used for every primitive in the overlay. */
    public static final RenderLayer BASIN_OVERLAY = RenderLayer.of(
            "terrain_diffusion_mc_basin_overlay",
            RenderSetup.builder(RenderPipelines.DEBUG_QUADS)
                    .translucent()
                    .build());

    private IrrigationBasinRenderLayers() {
    }
}

// futur update
//RenderSetup.builder(RenderPipelines.DEBUG_QUADS)
//    .translucent()
//    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)  // light Z-offset
//    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)                // draw on top
//    .build()