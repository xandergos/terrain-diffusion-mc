package com.github.xandergos.terraindiffusionmc.client.basin;

import com.github.xandergos.terraindiffusionmc.basin.IrrigationBasinBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * In-game settings screen for the irrigation basin (IBM) overlay.
 *
 * <p>Provides toggle buttons for the master overlay switch and individual layers,
 * a slider for the render radius (WIP) and a button to clear the tile cache.
 *
 * @author https://github.com/ThatDamnWittyWhizHard
 * @owner @ThatDamnWittyWhizHard
 */
public final class IrrigationBasinSettingsScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 4;

    private final Screen parentScreen;

    public IrrigationBasinSettingsScreen(Screen parentScreen) {
        super(Text.literal("Irrigation Basin Overlay"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int currentY = this.height / 2 - 70;  // réduit car moins de widgets

        addCenteredText(this.title, centerX, currentY, 0xFFFFFF);
        currentY += 20;

        addToggleRow(centerX, currentY, "Overlay", IrrigationBasinState.overlayEnabled,
                value -> IrrigationBasinState.overlayEnabled = value);
        currentY += BUTTON_HEIGHT + ROW_GAP;

        addToggleRow(centerX, currentY, "Basin Fill", IrrigationBasinState.showBasinFill,
                value -> IrrigationBasinState.showBasinFill = value);
        currentY += BUTTON_HEIGHT + ROW_GAP;

        addToggleRow(centerX, currentY, "Ridges", IrrigationBasinState.showRidges,
                value -> IrrigationBasinState.showRidges = value);
        currentY += BUTTON_HEIGHT + ROW_GAP;

        addRenderRadiusSlider(centerX, currentY);
        currentY += BUTTON_HEIGHT + ROW_GAP * 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Rebuild Cache"),
                        button -> IrrigationBasinBuilder.clearCache())
                .dimensions(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        currentY += BUTTON_HEIGHT + ROW_GAP;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    /**
     * Adds a toggle button that flips a boolean state and updates its own label.
     */
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

    /**
     * Adds a slider controlling {@link IrrigationBasinState#renderRadiusTiles} (range 1–3). WIP
     */
    private void addRenderRadiusSlider(int centerX, int y) {
        int min = 1;
        int max = 3;
        double normalized = (IrrigationBasinState.renderRadiusTiles - min) / (double) (max - min);
        SliderWidget slider = new SliderWidget(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Text.literal("Render radius: " + IrrigationBasinState.renderRadiusTiles + " tile(s)"), normalized) {
            @Override
            protected void updateMessage() {
                int radius = (int) Math.round(min + this.value * (max - min));
                this.setMessage(Text.literal("Render radius: " + radius + " tile(s)"));
            }

            @Override
            protected void applyValue() {
                IrrigationBasinState.renderRadiusTiles =
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

