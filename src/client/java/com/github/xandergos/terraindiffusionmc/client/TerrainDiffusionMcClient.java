package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.block.ModBlocks;
import com.github.xandergos.terraindiffusionmc.client.hydro.HydrologyKeybindings;
import com.github.xandergos.terraindiffusionmc.client.hydro.HydrologyRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.world.biome.GrassColors;

public class TerrainDiffusionMcClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HydrologyKeybindings.register();
        
        // Apply biome grass tint to the grass layer block. Gate on tintIndex == 0 so the
        // dirt-colored side base, the snow slab, and any other untinted faces are not
        // accidentally turned green.
        ColorProviderRegistry.BLOCK.register(
                (state, world, pos, tintIndex) -> {
                    if (tintIndex != 0) return -1;
                    return world != null && pos != null
                            ? BiomeColors.getGrassColor(world, pos)
                            : GrassColors.getDefaultColor();
                },
                ModBlocks.GRASS_LAYER
        );

        WorldRenderEvents.END_MAIN.register(context ->
                HydrologyRenderer.render(
                        context.matrices(),
                        (VertexConsumerProvider.Immediate) context.consumers(),
                        MinecraftClient.getInstance().gameRenderer.getCamera()
                )
        );
    }
}
