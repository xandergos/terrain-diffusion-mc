package com.github.xandergos.terraindiffusionmc.mixin.client;

import com.github.xandergos.terraindiffusionmc.client.WorldScaleSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
 *
 * <p>Vanilla limits the customize button to default/amplified/large_biomes presets and routes
 * its click through {@link CreateWorldScreen.WorldTab#openPresetEditor()}. We override three
 * lambda predicates plus the active-state setter to make the button visible+enabled for the
 * terrain_diffusion preset, then redirect the click to {@link WorldScaleSettingsScreen}.
 *
 * <p>The mixin targets use yarn-intermediary method names (method_48676, method_48680,
 * method_48681) and the synthetic outer-class field (field_42182) because Architectury Loom's
 * layered mappings keep intermediary names for synthetic / lambda members at runtime even
 * though source compiles against mojmap. Mixin's target resolution operates against the
 * runtime bytecode names. The named methods (openPresetEditor, getUiState) ARE remapped to
 * mojmap, which is why we use mojmap names for those.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class CreateWorldScreenWorldTabMixin {
    @Shadow
    @Final
    CreateWorldScreen field_42182;

    @Shadow
    private Button customizeTypeButton;

    private static final ResourceKey<WorldPreset> TERRAIN_DIFFUSION_PRESET_KEY =
            ResourceKey.create(Registries.WORLD_PRESET,
                    ResourceLocation.fromNamespaceAndPath("terrain-diffusion-mc", "terrain_diffusion"));

    @Inject(method = "method_48676", at = @At("TAIL"))
    private void terrainDiffusionMc$enableCustomizeButtonForTerrainDiffusion(WorldCreationUiState uiState, CallbackInfo callbackInfo) {
        if (isTerrainDiffusionWorldTypeSelected(uiState)) {
            customizeTypeButton.active = true;
        }
    }

    @Inject(method = "method_48680", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$forceCustomizeAvailable(CallbackInfoReturnable<Boolean> cir) {
        if (isTerrainDiffusionWorldTypeSelected()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "method_48681", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$forceCustomizeVisible(CallbackInfoReturnable<Boolean> cir) {
        if (isTerrainDiffusionWorldTypeSelected()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "openPresetEditor", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$openTerrainScaleScreen(CallbackInfo callbackInfo) {
        if (!isTerrainDiffusionWorldTypeSelected()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new WorldScaleSettingsScreen(field_42182));
            callbackInfo.cancel();
        }
    }

    private boolean isTerrainDiffusionWorldTypeSelected() {
        return isTerrainDiffusionWorldTypeSelected(field_42182.getUiState());
    }

    private static boolean isTerrainDiffusionWorldTypeSelected(WorldCreationUiState uiState) {
        if (uiState == null) {
            return false;
        }
        WorldCreationUiState.WorldTypeEntry worldType = uiState.getWorldType();
        if (worldType == null || worldType.preset() == null) {
            return false;
        }
        return worldType.preset().is(TERRAIN_DIFFUSION_PRESET_KEY);
    }
}
