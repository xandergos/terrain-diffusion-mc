package com.github.xandergos.terraindiffusionmc.client.hydro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers and dispatches keybindings for the hydrology overlay.
 *
 * <ul>
 *   <li>{@code B} : toggle the master overlay via {@link HydrologyState#toggleOverlay()}</li>
 *   <li>{@code N} : open {@link HydrologySettingsScreen} when no screen is open</li>
 * </ul>
 *
 * <p>Both bindings are registered under the {@code terrain-diffusion-mc} category. In 1.21.9+
 * keybinding categories are typed objects created via {@link KeyBinding.Category#create(Identifier)}.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologyKeybindings {

    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(
            Identifier.of("terrain-diffusion-mc", "main"));

    private static KeyBinding toggleOverlayKey;
    private static KeyBinding openSettingsKey;

    private HydrologyKeybindings() {}

    /** Registers both keybindings and hooks the tick listener. */
    public static void register() {
        toggleOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.terrain-diffusion-mc.toggle_hydro_overlay",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.terrain-diffusion-mc.open_hydro_settings",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(HydrologyKeybindings::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        while (toggleOverlayKey.wasPressed()) {
            HydrologyState.toggleOverlay();
        }
        while (openSettingsKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new HydrologySettingsScreen(null));
            }
        }
    }
}
