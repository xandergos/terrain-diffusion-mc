package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleSelectionState;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.dimension.DimensionType;

import java.util.HashMap;
import java.util.Map;

/**
 * World creation settings screen for selecting the initial terrain scale of a world.
 */
public final class WorldScaleSettingsScreen extends Screen {
    private static final String MOD_ID = "terrain-diffusion-mc";
    private static final int TEXT_FIELD_WIDTH = 80;
    private static final int TEXT_FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

    private static final Text LABEL_TEXT = Text.literal("World Scale");
    private static final Text DESCRIPTION_TEXT = Text.literal("Enter an integer value (1-6)");
    private static final Text ERROR_TEXT = Text.literal("Scale must be an integer between 1 and 6")
            .formatted(Formatting.RED);

    private final Screen parentScreen;
    private TextFieldWidget scaleTextField;
    private TextWidget validationTextWidget;

    public WorldScaleSettingsScreen(Screen parentScreen) {
        super(Text.translatable("terrain-diffusion-mc.world_settings.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addCenteredTextWidget(this.title, centerX, 20, 0xFFFFFF);

        addCenteredTextWidget(DESCRIPTION_TEXT, centerX, centerY - 34, 0xAAAAAA);
        addCenteredTextWidget(LABEL_TEXT, centerX, centerY - 22, 0xFFFFFF);

        scaleTextField = new TextFieldWidget(this.textRenderer,
                centerX - TEXT_FIELD_WIDTH / 2, centerY - 10,
                TEXT_FIELD_WIDTH, TEXT_FIELD_HEIGHT,
                LABEL_TEXT);
        scaleTextField.setText(String.valueOf(WorldScaleSelectionState.getPendingScaleOrDefault()));
        scaleTextField.setChangedListener(value -> validationTextWidget.setMessage(Text.empty()));
        this.addDrawableChild(scaleTextField);
        this.setInitialFocus(scaleTextField);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> onDonePressed())
                .dimensions(centerX - BUTTON_WIDTH - 5, centerY + 20, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
                .dimensions(centerX + 5, centerY + 20, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        validationTextWidget = new TextWidget(0, centerY + 46, this.width, 9, Text.empty(), this.textRenderer);
        this.addDrawableChild(validationTextWidget);
    }

    /**
     * Adds a centered TextWidget at the given screen-center x and y position.
     */
    private void addCenteredTextWidget(Text text, int centerX, int y, int color) {
        int textWidth = this.textRenderer.getWidth(text);
        MutableText coloredText = text.copy().styled(style -> style.withColor(color));
        TextWidget widget = new TextWidget(centerX - textWidth / 2, y, textWidth, 9, coloredText, this.textRenderer);
        this.addDrawableChild(widget);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parentScreen);
        }
    }

    /**
     * Parses and validates the chosen scale, then stores it as a pending world-creation value.
     */
    private void onDonePressed() {
        String rawScaleValue = scaleTextField.getText().trim();
        if (rawScaleValue.isEmpty()) {
            validationTextWidget.setMessage(ERROR_TEXT);
            return;
        }
        try {
            int selectedScale = Integer.parseInt(rawScaleValue);
            if (selectedScale < 1 || selectedScale > WorldScaleManager.MAX_SCALE) {
                validationTextWidget.setMessage(ERROR_TEXT);
                return;
            }
            applyWorldHeightForScale(selectedScale);
            WorldScaleSelectionState.setPendingScale(selectedScale);
            close();
        } catch (NumberFormatException exception) {
            validationTextWidget.setMessage(ERROR_TEXT);
        }
    }

    /**
     * Applies a pre-registered dimension type variant for the chosen scale.
     */
    private void applyWorldHeightForScale(int selectedScale) {
        if (!(parentScreen instanceof CreateWorldScreen createWorldScreen)) {
            return;
        }

        createWorldScreen.getWorldCreator().applyModifier((registryManager, selectedDimensions) -> {
            DimensionOptionsRegistryHolder updatedDimensions =
                    updateOverworldDimensionType(registryManager.getOrThrow(RegistryKeys.DIMENSION_TYPE),
                            selectedDimensions, selectedScale);
            return updatedDimensions == null ? selectedDimensions : updatedDimensions;
        });
    }

    /**
     * Replaces only the overworld dimension type entry with the scale-specific pre-registered one.
     */
    private DimensionOptionsRegistryHolder updateOverworldDimensionType(
            Registry<DimensionType> dimensionTypeRegistry,
            DimensionOptionsRegistryHolder selectedDimensions,
            int selectedScale
    ) {
        DimensionOptions overworldOptions = selectedDimensions.getOrEmpty(DimensionOptions.OVERWORLD).orElse(null);
        if (overworldOptions == null) {
            return null;
        }

        Identifier dimensionTypeId = Identifier.of(MOD_ID, "terrain_diffusion_scale_" + selectedScale);
        RegistryEntry.Reference<DimensionType> selectedDimensionTypeEntry = dimensionTypeRegistry.getEntry(dimensionTypeId).orElse(null);
        if (selectedDimensionTypeEntry == null) {
            return null;
        }

        DimensionOptions updatedOverworldOptions = new DimensionOptions(
                selectedDimensionTypeEntry,
                overworldOptions.chunkGenerator()
        );

        Map<net.minecraft.registry.RegistryKey<DimensionOptions>, DimensionOptions> updatedDimensionMap =
                new HashMap<>(selectedDimensions.dimensions());
        updatedDimensionMap.put(DimensionOptions.OVERWORLD, updatedOverworldOptions);
        return new DimensionOptionsRegistryHolder(updatedDimensionMap);
    }
}
