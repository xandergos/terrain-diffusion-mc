package com.github.xandergos.terraindiffusionmc.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class BiomeMixin {

    @Shadow
    public abstract float getTemperature();

    @Shadow
    public abstract boolean hasPrecipitation();

    @Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true)
    private void preventHighAltitudeSnow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (!this.hasPrecipitation()) {
            cir.setReturnValue(Biome.Precipitation.NONE);
            return;
        }

        // Base temperature >= 0.15 means this is NOT a snowy biome.
        // Always return RAIN to prevent altitude-based snow in non-snowy biomes.
        if (this.getTemperature() >= 0.15F) {
            cir.setReturnValue(Biome.Precipitation.RAIN);
        }
        // For snowy biomes (base temp < 0.15), let vanilla handle it
    }
}

