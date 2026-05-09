package com.github.xandergos.terraindiffusionmc.client.debug;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class TerrainDebugOverlayScreen extends Screen {
    private final Screen parent;
    private ButtonWidget categoryButton;
    private ButtonWidget modeButton;
    private ButtonWidget strideButton;
    private ButtonWidget alphaButton;
    private ButtonWidget radiusButton;

    public TerrainDebugOverlayScreen(Screen parent) {
        super(Text.literal("Terrain Debug Overlay"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 82;

        categoryButton = ButtonWidget.builder(categoryText(), button -> {
            TerrainDebugOverlayState.cycleCategory();
            updateButtonLabels();
        }).dimensions(centerX - 150, y, 300, 20).build();

        modeButton = ButtonWidget.builder(modeText(), button -> {
            TerrainDebugOverlayState.cycleMode();
            updateButtonLabels();
        }).dimensions(centerX - 150, y + 26, 300, 20).build();

        strideButton = ButtonWidget.builder(strideText(), button -> {
            TerrainDebugOverlayState.cycleStride();
            updateButtonLabels();
        }).dimensions(centerX - 150, y + 52, 300, 20).build();

        alphaButton = ButtonWidget.builder(alphaText(), button -> {
            TerrainDebugOverlayState.cycleFillAlpha();
            updateButtonLabels();
        }).dimensions(centerX - 150, y + 78, 300, 20).build();

        radiusButton = ButtonWidget.builder(radiusText(), button -> {
            TerrainDebugOverlayState.cycleRadius();
            updateButtonLabels();
        }).dimensions(centerX - 150, y + 104, 300, 20).build();

        addDrawableChild(categoryButton);
        addDrawableChild(modeButton);
        addDrawableChild(strideButton);
        addDrawableChild(alphaButton);
        addDrawableChild(radiusButton);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(centerX - 150, y + 138, 300, 20)
                .build());
    }

    private void updateButtonLabels() {
        categoryButton.setMessage(categoryText());
        modeButton.setMessage(modeText());
        strideButton.setMessage(strideText());
        alphaButton.setMessage(alphaText());
        radiusButton.setMessage(radiusText());
    }

    private static Text categoryText() {
        return Text.literal("Category: " + TerrainDebugOverlayState.category().label());
    }

    private static Text modeText() {
        return Text.literal("Mode: " + TerrainDebugOverlayState.mode().label());
    }

    private static Text strideText() {
        return Text.literal("Surface resolution: " + TerrainDebugOverlayState.stride() + " block(s)");
    }

    private static Text alphaText() {
        return Text.literal("Surface opacity: " + TerrainDebugOverlayState.fillAlpha());
    }

    private static Text radiusText() {
        return Text.literal("Radius: " + TerrainDebugOverlayState.radiusTiles() + " tile(s)");
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
