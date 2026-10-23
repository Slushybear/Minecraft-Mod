package com.slushybear.daedalus.mixin;

import com.slushybear.daedalus.module.ModuleRegistry;
import com.slushybear.daedalus.module.visuals.Freecam;
import com.slushybear.daedalus.module.visuals.Freelook;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera placement. Vanilla's alignWithEntity puts the camera at the player's eyes each frame (and behind them in
 * third person); after it runs, Freecam or Freelook may move it. Camera tweaks change the third person distance and
 * whether walls push the camera in.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private Level level;
    @Shadow private Entity entity;
    @Shadow private boolean detached;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(Vec3 position);

    @Shadow protected abstract void move(float forwards, float up, float right);

    @Shadow protected abstract float getMaxZoom(float cameraDistance);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void daedalus$freeCamera(float partialTick, CallbackInfo ci) {
        Freecam freecam = ModuleRegistry.FREECAM;
        if (freecam.active()) {
            setRotation(freecam.yaw(), freecam.pitch());
            setPosition(freecam.cameraPos(partialTick));
            // Detached makes the game draw your own player and hide the first person hand.
            detached = true;
            return;
        }
        Freelook freelook = ModuleRegistry.FREELOOK;
        if (freelook.active() && entity != null) {
            setRotation(freelook.yaw(), freelook.pitch());
            setPosition(entity.getEyePosition(partialTick));
            detached = true;
            // Always the full distance: freelook goes through blocks instead of being pushed in front of them.
            move(-ModuleRegistry.CAMERA.distance(), 0, 0);
        }
    }

    /**
     * "Smart culling" works out which chunk sections are visible by spreading outwards from the camera. Started from
     * inside a solid block it finds almost nothing, so the world vanishes or flickers. Vanilla only turns it off for
     * spectators inside blocks; Freecam turns it off while flying, and Freelook while its camera is inside a block.
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void daedalus$noSmartCull(CameraRenderState state, float partialTick, CallbackInfo ci) {
        if (ModuleRegistry.FREECAM.active()) {
            state.smartCull = false;
        } else if (ModuleRegistry.FREELOOK.active() && level != null
                && level.getBlockState(state.blockPos).isSolidRender()) {
            state.smartCull = false;
        }
    }

    /** Scales vanilla's third person distance. */
    @ModifyArg(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float daedalus$distance(float distance) {
        return distance * ModuleRegistry.CAMERA.distanceScale();
    }

    /** getMaxZoom pulls the camera in front of walls; with clipping on it keeps the full distance. */
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void daedalus$clip(float distance, CallbackInfoReturnable<Float> cir) {
        if (ModuleRegistry.CAMERA.clipThroughWalls()) {
            cir.setReturnValue(distance);
        }
    }
}
