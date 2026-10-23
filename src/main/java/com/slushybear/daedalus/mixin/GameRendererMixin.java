package com.slushybear.daedalus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slushybear.daedalus.module.ModuleRegistry;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** bobHurt tilts the view when you take damage. Skipping it removes the shake. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void daedalus$noHurtShake(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
        if (ModuleRegistry.CAMERA.noHurtShake()) {
            ci.cancel();
        }
    }
}
