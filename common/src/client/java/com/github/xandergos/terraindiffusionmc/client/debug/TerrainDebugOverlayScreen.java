package com.github.xandergos.terraindiffusionmc.client.debug;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class TerrainDebugOverlayScreen extends Screen {
    private final Screen parent;
    private Button categoryButton;
    private Button modeButton;
    private Button strideButton;
    private Button alphaButton;
    private Button radiusButton;

    public TerrainDebugOverlayScreen(Screen parent) {
        super(Component.literal("Terrain Debug Overlay"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 82;

        categoryButton = Button.builder(categoryText(), button -> {
            TerrainDebugOverlayState.cycleCategory();
            updateButtonLabels();
        }).bounds(centerX - 150, y, 300, 20).build();

        modeButton = Button.builder(modeText(), button -> {
            TerrainDebugOverlayState.cycleMode();
            updateButtonLabels();
        }).bounds(centerX - 150, y + 26, 300, 20).build();

        strideButton = Button.builder(strideText(), button -> {
            TerrainDebugOverlayState.cycleStride();
            updateButtonLabels();
        }).bounds(centerX - 150, y + 52, 300, 20).build();

        alphaButton = Button.builder(alphaText(), button -> {
            TerrainDebugOverlayState.cycleFillAlpha();
            updateButtonLabels();
        }).bounds(centerX - 150, y + 78, 300, 20).build();

        radiusButton = Button.builder(radiusText(), button -> {
            TerrainDebugOverlayState.cycleRadius();
            updateButtonLabels();
        }).bounds(centerX - 150, y + 104, 300, 20).build();

        addRenderableWidget(categoryButton);
        addRenderableWidget(modeButton);
        addRenderableWidget(strideButton);
        addRenderableWidget(alphaButton);
        addRenderableWidget(radiusButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(centerX - 150, y + 138, 300, 20)
                .build());
    }

    private void updateButtonLabels() {
        categoryButton.setMessage(categoryText());
        modeButton.setMessage(modeText());
        strideButton.setMessage(strideText());
        alphaButton.setMessage(alphaText());
        radiusButton.setMessage(radiusText());
    }

    private static Component categoryText() {
        return Component.literal("Category: " + TerrainDebugOverlayState.category().label());
    }

    private static Component modeText() {
        return Component.literal("Mode: " + TerrainDebugOverlayState.mode().label());
    }

    private static Component strideText() {
        return Component.literal("Surface resolution: " + TerrainDebugOverlayState.stride() + " block(s)");
    }

    private static Component alphaText() {
        return Component.literal("Surface opacity: " + TerrainDebugOverlayState.fillAlpha());
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
