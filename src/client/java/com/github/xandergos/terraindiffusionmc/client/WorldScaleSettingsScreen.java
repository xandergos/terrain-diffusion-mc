package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.world.HeightConverter;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleSelectionState;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * World creation settings screen for selecting the initial terrain scale of a world.
 */
public final class WorldScaleSettingsScreen extends Screen {
    private static final int TEXT_FIELD_WIDTH = 80;
    private static final int TEXT_FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

    private static final Text LABEL_TEXT = Text.literal("World Scale");
    private static final Text DESCRIPTION_TEXT = Text.literal("Enter an integer value >= 1");
    private static final Text ERROR_TEXT = Text.literal("Scale must be an integer >= 1")
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
        validationTextWidget.setTextColor(0xFF5555);
        this.addDrawableChild(validationTextWidget);
    }

    /**
     * Adds a centered TextWidget at the given screen-center x and y position.
     */
    private void addCenteredTextWidget(Text text, int centerX, int y, int color) {
        int textWidth = this.textRenderer.getWidth(text);
        TextWidget widget = new TextWidget(centerX - textWidth / 2, y, textWidth, 9, text, this.textRenderer);
        widget.setTextColor(color);
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
            if (selectedScale < 1) {
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
     * Applies per-world dimension and generator height based on the chosen scale.
     */
    private void applyWorldHeightForScale(int selectedScale) {
        if (!(parentScreen instanceof CreateWorldScreen createWorldScreen)) {
            return;
        }

        createWorldScreen.getWorldCreator().applyModifier((registryManager, selectedDimensions) -> {
            DimensionOptionsRegistryHolder updatedDimensions = updateOverworldHeight(selectedDimensions, selectedScale);
            return updatedDimensions == null ? selectedDimensions : updatedDimensions;
        });
    }

    private DimensionOptionsRegistryHolder updateOverworldHeight(DimensionOptionsRegistryHolder selectedDimensions, int selectedScale) {
        DimensionOptions overworldOptions = selectedDimensions.getOrEmpty(DimensionOptions.OVERWORLD).orElse(null);
        if (overworldOptions == null) {
            return null;
        }

        DimensionType baseDimensionType = overworldOptions.dimensionTypeEntry().value();
        int minimumY = baseDimensionType.minY();
        int maxGeneratedY = HeightConverter.getMaxGeneratedYForScale(selectedScale);
        int requiredHeight = alignToSectionHeight(maxGeneratedY - minimumY + 1);
        int maxAllowedHeight = Math.min(DimensionType.MAX_HEIGHT, DimensionType.MAX_COLUMN_HEIGHT - minimumY);
        int boundedHeight = Math.max(16, Math.min(requiredHeight, maxAllowedHeight));
        int boundedLogicalHeight = boundedHeight;

        DimensionType updatedDimensionType = new DimensionType(
                baseDimensionType.fixedTime(),
                baseDimensionType.hasSkyLight(),
                baseDimensionType.hasCeiling(),
                baseDimensionType.ultrawarm(),
                baseDimensionType.natural(),
                baseDimensionType.coordinateScale(),
                baseDimensionType.bedWorks(),
                baseDimensionType.respawnAnchorWorks(),
                minimumY,
                boundedHeight,
                boundedLogicalHeight,
                baseDimensionType.infiniburn(),
                baseDimensionType.effects(),
                baseDimensionType.ambientLight(),
                baseDimensionType.cloudHeight(),
                baseDimensionType.monsterSettings()
        );

        ChunkGenerator updatedChunkGenerator = updateChunkGeneratorHeight(overworldOptions.chunkGenerator(), minimumY, boundedHeight);
        DimensionOptions updatedOverworldOptions = new DimensionOptions(RegistryEntry.of(updatedDimensionType), updatedChunkGenerator);

        Map<RegistryKey<DimensionOptions>, DimensionOptions> updatedDimensionMap = new HashMap<>(selectedDimensions.dimensions());
        updatedDimensionMap.put(DimensionOptions.OVERWORLD, updatedOverworldOptions);
        return new DimensionOptionsRegistryHolder(updatedDimensionMap);
    }

    private ChunkGenerator updateChunkGeneratorHeight(ChunkGenerator chunkGenerator, int minimumY, int boundedHeight) {
        if (!(chunkGenerator instanceof NoiseChunkGenerator noiseChunkGenerator)) {
            return chunkGenerator;
        }

        ChunkGeneratorSettings baseSettings = noiseChunkGenerator.getSettings().value();
        GenerationShapeConfig baseShapeConfig = baseSettings.generationShapeConfig();
        GenerationShapeConfig updatedShapeConfig = new GenerationShapeConfig(
                minimumY,
                boundedHeight,
                baseShapeConfig.horizontalSize(),
                baseShapeConfig.verticalSize()
        );

        ChunkGeneratorSettings updatedSettings = new ChunkGeneratorSettings(
                updatedShapeConfig,
                baseSettings.defaultBlock(),
                baseSettings.defaultFluid(),
                baseSettings.noiseRouter(),
                baseSettings.surfaceRule(),
                baseSettings.spawnTarget(),
                baseSettings.seaLevel(),
                baseSettings.mobGenerationDisabled(),
                baseSettings.aquifers(),
                baseSettings.oreVeins(),
                baseSettings.usesLegacyRandom()
        );

        return new NoiseChunkGenerator(noiseChunkGenerator.getBiomeSource(), RegistryEntry.of(updatedSettings));
    }

    private int alignToSectionHeight(int requiredHeight) {
        int sectionHeight = 16;
        return ((requiredHeight + sectionHeight - 1) / sectionHeight) * sectionHeight;
    }
}
