package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.client.debug.TerrainDebugOverlayRenderer;
import com.github.xandergos.terraindiffusionmc.client.debug.TerrainDebugOverlayScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class TerrainDiffusionMcClient implements ClientModInitializer {
    private static final String OPEN_DEBUG_OVERLAY_KEY_ID =
            "key.terrain-diffusion-mc.open_terrain_debug_overlay_f8";

    private static KeyBinding openDebugOverlayMenuKey;

    @Override
    public void onInitializeClient() {
        openDebugOverlayMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                OPEN_DEBUG_OVERLAY_KEY_ID,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KeyBinding.Category.DEBUG
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDebugOverlayMenuKey.wasPressed()) {
                client.setScreen(new TerrainDebugOverlayScreen(client.currentScreen));
            }
        });

        TerrainDebugOverlayRenderer.register();
    }
}
