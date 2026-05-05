package com.github.xandergos.terraindiffusionmc.mixin.client;

import com.github.xandergos.terraindiffusionmc.debug.TerrainDebugStats;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.List;

@Mixin(DebugHud.class)
public abstract class DebugHudMixin {
    @ModifyArgs(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/DebugHud;drawText(Lnet/minecraft/client/gui/DrawContext;Ljava/util/List;Z)V"
            )
    )
    private void terrainDiffusion$appendDebugLines(Args args) {
        boolean leftSide = args.get(2);
        if (!leftSide) return;

        List<String> original = args.get(1);
        List<String> lines = new ArrayList<>(original.size() + 8);
        lines.addAll(original);
        lines.add("");
        lines.addAll(TerrainDebugStats.f3Lines());
        args.set(1, lines);
    }
}
