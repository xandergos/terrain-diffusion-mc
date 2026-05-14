package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.TerrainDiffusionMc;
import com.github.xandergos.terraindiffusionmc.client.debug.TerrainDebugOverlayRenderer;
import com.github.xandergos.terraindiffusionmc.client.debug.TerrainDebugOverlayScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = TerrainDiffusionMc.FML_MOD_ID, dist = Dist.CLIENT)
public final class TerrainDiffusionMcClient {
    private static final String OPEN_DEBUG_OVERLAY_KEY_ID = "key.terrain-diffusion-mc.open_terrain_debug_overlay_f8";
    private static final KeyMapping.Category DEBUG_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(TerrainDiffusionMc.MOD_ID, "debug")
    );

    private static KeyMapping openDebugOverlayMenuKey;

    public TerrainDiffusionMcClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        TerrainDebugOverlayRenderer.register();
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openDebugOverlayMenuKey = new KeyMapping(
                OPEN_DEBUG_OVERLAY_KEY_ID,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                DEBUG_CATEGORY
        );
        event.registerCategory(DEBUG_CATEGORY);
        event.register(openDebugOverlayMenuKey);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (openDebugOverlayMenuKey == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        while (openDebugOverlayMenuKey.consumeClick()) {
            client.setScreen(new TerrainDebugOverlayScreen(client.screen));
        }
    }
}
