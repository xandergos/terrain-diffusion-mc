package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.client.basin.IrrigationBasinKeybindings;
import com.github.xandergos.terraindiffusionmc.client.basin.IrrigationBasinRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;

public class TerrainDiffusionMcClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        IrrigationBasinKeybindings.register();
        WorldRenderEvents.END_MAIN.register(context ->
                IrrigationBasinRenderer.render(
                        context.matrices(),
                        (VertexConsumerProvider.Immediate) context.consumers(),
                        MinecraftClient.getInstance().gameRenderer.getCamera()
                )
        );
    }
}
