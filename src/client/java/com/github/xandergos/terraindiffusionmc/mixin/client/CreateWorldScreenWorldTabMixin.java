package com.github.xandergos.terraindiffusionmc.mixin.client;

import com.github.xandergos.terraindiffusionmc.client.WorldScaleSettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.registry.RegistryKey;
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
 * NOTE FOR MC 1.21.1: The intermediary names below (field_42182, method_48676,
 * method_48680, method_48681) were valid for MC 1.21.11. You must verify the
 * correct 1.21.1 intermediary names via the Yarn mappings at:
 *   https://github.com/FabricMC/yarn/tree/1.21.1+build.3/mappings/net/minecraft/client/gui/screen/world
 * or by running: ./gradlew genSources and inspecting CreateWorldScreen$WorldTab.
 */
@Mixin(targets = "net.minecraft.client.gui.screen.world.CreateWorldScreen$WorldTab")
public abstract class CreateWorldScreenWorldTabMixin {
    @Shadow
    @Final
    CreateWorldScreen field_42182;

    @Shadow
    private ButtonWidget customizeButton;

    private static final RegistryKey<WorldPreset> TERRAIN_DIFFUSION_PRESET_KEY =
            RegistryKey.of(RegistryKeys.WORLD_PRESET, Identifier.of("terrain-diffusion-mc", "terrain_diffusion"));

    @Inject(method = "method_48676", at = @At("TAIL"))
    private void terrainDiffusionMc$enableCustomizeButtonForTerrainDiffusion(WorldCreator worldCreator, CallbackInfo callbackInfo) {
        if (isTerrainDiffusionWorldTypeSelected()) {
            customizeButton.active = true;
        }
    }

    @Inject(method = "method_48680", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$forceCustomizeAvailable(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (isTerrainDiffusionWorldTypeSelected()) {
            callbackInfoReturnable.setReturnValue(true);
        }
    }

    @Inject(method = "method_48681", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$forceCustomizeVisible(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (isTerrainDiffusionWorldTypeSelected()) {
            callbackInfoReturnable.setReturnValue(true);
        }
    }

    @Inject(method = "openCustomizeScreen", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$openTerrainScaleScreen(CallbackInfo callbackInfo) {
        if (!isTerrainDiffusionWorldTypeSelected()) {
            return;
        }
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient != null) {
            minecraftClient.setScreen(new WorldScaleSettingsScreen(field_42182));
            callbackInfo.cancel();
        }
    }

    private boolean isTerrainDiffusionWorldTypeSelected() {
        WorldCreator worldCreator = field_42182.getWorldCreator();
        if (worldCreator == null) {
            return false;
        }
        WorldCreator.WorldType worldType = worldCreator.getWorldType();
        if (worldType == null) {
            return false;
        }
        if (TERRAIN_DIFFUSION_PRESET_KEY.equals(worldType.preset())) {
            return true;
        }
        return "terrain diffusion".equalsIgnoreCase(worldType.getName().getString());
    }
}
