package com.github.xandergos.terraindiffusionmc.client.hydro;

import com.github.xandergos.terraindiffusionmc.hydro.HydrologyBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * In-game settings screen for the hydrology debug overlay.
 *
 * <p>Provides toggle buttons for the master overlay switch and individual layers (rivers, flow
 * arrows and pit-fill diff), a slider for the render and a button
 * to clear the tile cache.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class HydrologySettingsScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 4;

    private final Screen parentScreen;

    public HydrologySettingsScreen(Screen parentScreen) {
        super(Text.literal("Hydrology Overlay"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int currentY = this.height / 2 - 110;

        addCenteredText(this.title, centerX, currentY, 0xFFFFFF);
        currentY += 20;

        addToggleRow(centerX, currentY, "Overlay", HydrologyState.overlayEnabled,
                value -> HydrologyState.overlayEnabled = value);
        currentY += BUTTON_HEIGHT + ROW_GAP;

        addToggleRow(centerX, currentY, "Rivers", HydrologyState.showRivers,
                value -> HydrologyState.showRivers = value);
        currentY += BUTTON_HEIGHT + ROW_GAP;

        addToggleRow(centerX, currentY, "Flow Arrows", HydrologyState.showFlowArrows,
                value -> HydrologyState.showFlowArrows = value);
        currentY += BUTTON_HEIGHT + ROW_GAP;

        addToggleRow(centerX, currentY, "Pit-fill Delta", HydrologyState.showFilledDelta,
                value -> HydrologyState.showFilledDelta = value);
        currentY += BUTTON_HEIGHT + ROW_GAP;

        addRenderRadiusSlider(centerX, currentY);
        currentY += BUTTON_HEIGHT + ROW_GAP * 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Rebuild Cache"),
                        button -> HydrologyBuilder.clearCache())
                .dimensions(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        currentY += BUTTON_HEIGHT + ROW_GAP;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void addToggleRow(int centerX, int y, String labelPrefix, boolean initialValue,
                              java.util.function.Consumer<Boolean> onChange) {
        boolean[] currentValue = {initialValue};
        ButtonWidget[] buttonRef = new ButtonWidget[1];
        buttonRef[0] = ButtonWidget.builder(formatToggleLabel(labelPrefix, currentValue[0]),
                        button -> {
                            currentValue[0] = !currentValue[0];
                            onChange.accept(currentValue[0]);
                            button.setMessage(formatToggleLabel(labelPrefix, currentValue[0]));
                        })
                .dimensions(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(buttonRef[0]);
    }

    private static Text formatToggleLabel(String prefix, boolean value) {
        return Text.literal(prefix + ": " + (value ? "ON" : "OFF"));
    }

    private void addRenderRadiusSlider(int centerX, int y) {
        int min = 64;
        int max = 768;
        double normalized = (HydrologyState.renderRadius - min) / (double) (max - min);
        SliderWidget slider = new SliderWidget(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Text.literal("Render radius: " + HydrologyState.renderRadius + " blocks"), normalized) {
            @Override
            protected void updateMessage() {
                int value = (int) Math.round(min + this.value * (max - min));
                this.setMessage(Text.literal("Render radius: " + value + " blocks"));
            }

            @Override
            protected void applyValue() {
                HydrologyState.renderRadius =
                        (int) Math.round(min + this.value * (max - min));
            }
        };
        this.addDrawableChild(slider);
    }

    private void addCenteredText(Text text, int centerX, int y, int color) {
        int textWidth = this.textRenderer.getWidth(text);
        MutableText coloredText = text.copy().styled(style -> style.withColor(color));
        TextWidget widget = new TextWidget(centerX - textWidth / 2, y, textWidth, 9, coloredText, this.textRenderer);
        this.addDrawableChild(widget);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parentScreen);
    }
}