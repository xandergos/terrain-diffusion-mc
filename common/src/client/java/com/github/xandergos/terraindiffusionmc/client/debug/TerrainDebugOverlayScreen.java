package com.github.xandergos.terraindiffusionmc.client.debug;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TerrainDebugOverlayScreen extends Screen {
    private final Screen parent;
    private Button modeButton;
    private Button radiusButton;

    public TerrainDebugOverlayScreen(Screen parent) {
        super(Component.literal("Terrain River Debug Overlay"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 42;

        modeButton = Button.builder(modeText(), button -> {
            TerrainDebugOverlayState.cycleMode();
            updateButtonLabels();
        }).bounds(centerX - 150, y, 300, 20).build();

        radiusButton = Button.builder(radiusText(), button -> {
            TerrainDebugOverlayState.cycleRadius();
            updateButtonLabels();
        }).bounds(centerX - 150, y + 26, 300, 20).build();

        addRenderableWidget(modeButton);
        addRenderableWidget(radiusButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(centerX - 150, y + 60, 300, 20)
                .build());
    }

    private void updateButtonLabels() {
        modeButton.setMessage(modeText());
        radiusButton.setMessage(radiusText());
    }

    private static Component modeText() {
        return Component.literal("Mode: " + TerrainDebugOverlayState.mode().label());
    }

    private static Component radiusText() {
        return Component.literal("Radius: " + TerrainDebugOverlayState.radiusTiles() + " tile(s)");
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
