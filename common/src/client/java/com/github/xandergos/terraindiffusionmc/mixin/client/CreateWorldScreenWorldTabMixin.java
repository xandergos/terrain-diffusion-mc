package com.github.xandergos.terraindiffusionmc.mixin.client;

import com.github.xandergos.terraindiffusionmc.client.WorldScaleSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reuses vanilla's World tab "Customize" button for Terrain Diffusion worlds.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class CreateWorldScreenWorldTabMixin {
    @Shadow
    @Final
    private Button customizeTypeButton;

    @Unique
    private CreateWorldScreen terrainDiffusionMc$createWorldScreen;

    private static final ResourceKey<WorldPreset> TERRAIN_DIFFUSION_PRESET_KEY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "terrain_diffusion"));

    @Inject(method = "<init>", at = @At("RETURN"))
    private void terrainDiffusionMc$captureCreateWorldScreen(CreateWorldScreen createWorldScreen, CallbackInfo callbackInfo) {
        this.terrainDiffusionMc$createWorldScreen = createWorldScreen;
        createWorldScreen.getUiState().addListener(state -> terrainDiffusionMc$refreshCustomizeButton());
        terrainDiffusionMc$refreshCustomizeButton();
    }

    @Unique
    private void terrainDiffusionMc$refreshCustomizeButton() {
        if (isTerrainDiffusionWorldTypeSelected()) {
            this.customizeTypeButton.active = true;
        }
    }

    @Inject(method = "openPresetEditor", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$openTerrainScaleScreen(CallbackInfo callbackInfo) {
        if (!isTerrainDiffusionWorldTypeSelected()) {
            return;
        }
        Minecraft minecraftClient = Minecraft.getInstance();
        if (minecraftClient != null && terrainDiffusionMc$createWorldScreen != null) {
            minecraftClient.setScreen(new WorldScaleSettingsScreen(terrainDiffusionMc$createWorldScreen));
            callbackInfo.cancel();
        }
    }

    private boolean isTerrainDiffusionWorldTypeSelected() {
        if (terrainDiffusionMc$createWorldScreen == null) {
            return false;
        }
        WorldCreationUiState worldCreator = terrainDiffusionMc$createWorldScreen.getUiState();
        if (worldCreator == null) {
            return false;
        }
        WorldCreationUiState.WorldTypeEntry worldType = worldCreator.getWorldType();
        if (worldType == null) {
            return false;
        }
        if (worldType.preset().unwrapKey().map(TERRAIN_DIFFUSION_PRESET_KEY::equals).orElse(false)) {
            return true;
        }
        return "terrain diffusion".equalsIgnoreCase(worldType.describePreset().getString());
    }
}
