package com.github.xandergos.terraindiffusionmc.client.hydro;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

/**
 * Render layer used by {@link HydrologyRenderer}.
 *
 * <p>A single {@link RenderPipelines#DEBUG_QUADS} pipeline (vertex format {@code POSITION_COLOR})
 * is used for every primitive : river segments, flow arrows and pit-fill diff highlights so
 * a single buffer flush draws the whole overlay.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyRenderLayers {

    /** Translucent quad layer used for every primitive in the overlay. */
    public static final RenderLayer HYDRO_OVERLAY = RenderLayer.of(
            "terrain_diffusion_mc_hydro_overlay",
            RenderSetup.builder(RenderPipelines.DEBUG_QUADS)
                    .translucent()
                    .build());

    private HydrologyRenderLayers() {}
}
