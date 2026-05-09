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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reuses vanilla's World tab "Customize" button for Terrain Diffusion worlds.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class CreateWorldScreenWorldTabMixin {
    @Shadow(remap = false)
    @Final
    CreateWorldScreen field_42182;

    @Shadow
    private Button customizeTypeButton;

    private static final ResourceKey<WorldPreset> TERRAIN_DIFFUSION_PRESET_KEY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "terrain_diffusion"));

    @Inject(method = "method_48676", at = @At("TAIL"), remap = false)
    private void terrainDiffusionMc$enableCustomizeButtonForTerrainDiffusion(WorldCreationUiState worldCreator, CallbackInfo callbackInfo) {
        if (isTerrainDiffusionWorldTypeSelected()) {
            customizeTypeButton.active = true;
        }
    }

    @Inject(method = "method_48680", at = @At("HEAD"), cancellable = true, remap = false)
    private void terrainDiffusionMc$forceCustomizeAvailable(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (isTerrainDiffusionWorldTypeSelected()) {
            callbackInfoReturnable.setReturnValue(true);
        }
    }

    @Inject(method = "method_48681", at = @At("HEAD"), cancellable = true, remap = false)
    private void terrainDiffusionMc$forceCustomizeVisible(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (isTerrainDiffusionWorldTypeSelected()) {
            callbackInfoReturnable.setReturnValue(true);
        }
    }

    @Inject(method = "openPresetEditor", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$openTerrainScaleScreen(CallbackInfo callbackInfo) {
        if (!isTerrainDiffusionWorldTypeSelected()) {
            return;
        }
        Minecraft minecraftClient = Minecraft.getInstance();
        if (minecraftClient != null) {
            minecraftClient.setScreen(new WorldScaleSettingsScreen(field_42182));
            callbackInfo.cancel();
        }
    }

    private boolean isTerrainDiffusionWorldTypeSelected() {
        WorldCreationUiState worldCreator = field_42182.getUiState();
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
