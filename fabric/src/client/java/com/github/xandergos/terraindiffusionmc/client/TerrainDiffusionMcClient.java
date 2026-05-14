package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.client.debug.TerrainDebugOverlayRenderer;
import com.github.xandergos.terraindiffusionmc.client.debug.TerrainDebugOverlayScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class TerrainDiffusionMcClient implements ClientModInitializer {
    private static final String MOD_ID = "terrain-diffusion-mc";
    private static final String OPEN_DEBUG_OVERLAY_KEY_ID = "key.terrain-diffusion-mc.open_terrain_debug_overlay_f8";
    private static final KeyMapping.Category DEBUG_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "debug")
    );

    private static KeyMapping openDebugOverlayMenuKey;

    @Override
    public void onInitializeClient() {
        openDebugOverlayMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                OPEN_DEBUG_OVERLAY_KEY_ID,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                DEBUG_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDebugOverlayMenuKey.consumeClick()) {
                client.setScreen(new TerrainDebugOverlayScreen(client.screen));
            }
        });

        TerrainDebugOverlayRenderer.register();
    }
}
