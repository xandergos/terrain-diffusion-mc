//package com.github.xandergos.terraindiffusionmc.mixin.client;
//
//import com.github.xandergos.terraindiffusionmc.client.basin.IrrigationBasinRenderer;
//import net.minecraft.client.MinecraftClient;
//import net.minecraft.client.render.Camera;
//import net.minecraft.client.render.VertexConsumerProvider;
//import net.minecraft.client.render.WorldRenderer;
//import net.minecraft.client.render.state.WorldRenderState;
//import net.minecraft.client.util.math.MatrixStack;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//
///**
// * Hooks the irrigation basin overlay into the world render after the main pass has finished
// * uploading its translucent and entity quads, so the overlay sits on top of terrain.
// */
//@Mixin(WorldRenderer.class)
//public abstract class WorldRendererMixin {
//    @Inject(method = "renderMain", at = @At("TAIL"))
//    private void terrainDiffusionMc$renderIrrigationBasinOverlay(
//            VertexConsumerProvider.Immediate immediate,
//            MatrixStack matrices,
//            boolean renderBlockOutline,
//            WorldRenderState renderStates,
//            CallbackInfo callbackInfo) {
//        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
//        IrrigationBasinRenderer.render(matrices, immediate, camera);
//    }
//}
