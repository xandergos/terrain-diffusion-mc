package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleSelectionState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

import java.util.HashMap;
import java.util.Map;

/**
 * World creation settings screen for selecting the initial terrain scale of a world.
 */
public final class WorldScaleSettingsScreen extends Screen {
    private static final String NAMESPACE = "terrain-diffusion-mc";
    private static final int TEXT_FIELD_WIDTH = 80;
    private static final int TEXT_FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

    private static final Component LABEL_TEXT = Component.literal("World Scale");
    private static final Component DESCRIPTION_TEXT = Component.literal("Enter an integer value (1-6)");
    private static final Component ERROR_TEXT = Component.literal("Scale must be an integer between 1 and 6")
            .withStyle(ChatFormatting.RED);

    private final Screen parentScreen;
    private EditBox scaleTextField;
    private StringWidget validationTextWidget;

    public WorldScaleSettingsScreen(Screen parentScreen) {
        super(Component.translatable("terrain-diffusion-mc.world_settings.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addCenteredTextWidget(this.title, centerX, 20, 0xFFFFFF);

        addCenteredTextWidget(DESCRIPTION_TEXT, centerX, centerY - 34, 0xAAAAAA);
        addCenteredTextWidget(LABEL_TEXT, centerX, centerY - 22, 0xFFFFFF);

        scaleTextField = new EditBox(this.font,
                centerX - TEXT_FIELD_WIDTH / 2, centerY - 10,
                TEXT_FIELD_WIDTH, TEXT_FIELD_HEIGHT,
                LABEL_TEXT);
        scaleTextField.setValue(String.valueOf(WorldScaleSelectionState.getPendingScaleOrDefault()));
        scaleTextField.setResponder(value -> validationTextWidget.setMessage(Component.empty()));
        this.addRenderableWidget(scaleTextField);
        this.setInitialFocus(scaleTextField);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onDonePressed())
                .bounds(centerX - BUTTON_WIDTH - 5, centerY + 20, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(centerX + 5, centerY + 20, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        validationTextWidget = new StringWidget(0, centerY + 46, this.width, 9, Component.empty(), this.font);
        this.addRenderableWidget(validationTextWidget);
    }

    private void addCenteredTextWidget(Component text, int centerX, int y, int color) {
        int textWidth = this.font.width(text);
        MutableComponent coloredText = text.copy().withStyle(style -> style.withColor(color));
        StringWidget widget = new StringWidget(centerX - textWidth / 2, y, textWidth, 9, coloredText, this.font);
        this.addRenderableWidget(widget);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    private void onDonePressed() {
        String rawScaleValue = scaleTextField.getValue().trim();
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
            onClose();
        } catch (NumberFormatException exception) {
            validationTextWidget.setMessage(ERROR_TEXT);
        }
    }

    private void applyWorldHeightForScale(int selectedScale) {
        if (!(parentScreen instanceof CreateWorldScreen createWorldScreen)) {
            return;
        }

        createWorldScreen.getUiState().updateDimensions((registryAccess, selectedDimensions) -> {
            Registry<DimensionType> dimensionTypeRegistry = registryAccess.registryOrThrow(Registries.DIMENSION_TYPE);
            WorldDimensions updated = updateOverworldDimensionType(dimensionTypeRegistry, selectedDimensions, selectedScale);
            return updated == null ? selectedDimensions : updated;
        });
    }

    /**
     * Replaces only the overworld dimension type entry with the scale-specific pre-registered one.
     */
    private WorldDimensions updateOverworldDimensionType(
            Registry<DimensionType> dimensionTypeRegistry,
            WorldDimensions selectedDimensions,
            int selectedScale
    ) {
        LevelStem overworldStem = selectedDimensions.get(LevelStem.OVERWORLD).orElse(null);
        if (overworldStem == null) {
            return null;
        }

        ResourceLocation dimensionTypeId = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "terrain_diffusion_scale_" + selectedScale);
        Holder.Reference<DimensionType> selectedDimensionTypeEntry = dimensionTypeRegistry
                .getHolder(dimensionTypeId)
                .orElse(null);
        if (selectedDimensionTypeEntry == null) {
            return null;
        }

        LevelStem updatedOverworldStem = new LevelStem(selectedDimensionTypeEntry, overworldStem.generator());

        Map<ResourceKey<LevelStem>, LevelStem> updatedDimensionMap = new HashMap<>(selectedDimensions.dimensions());
        updatedDimensionMap.put(LevelStem.OVERWORLD, updatedOverworldStem);
        return new WorldDimensions(updatedDimensionMap);
    }
}
