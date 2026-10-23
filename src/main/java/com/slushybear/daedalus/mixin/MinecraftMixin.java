package com.slushybear.daedalus.mixin;

import com.slushybear.daedalus.module.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** The game asks this for every entity it draws; saying yes gives it the glowing outline, visible through walls. */
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void daedalus$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ModuleRegistry.MOB_ESP.glows(entity)) {
            cir.setReturnValue(true);
        }
    }

    // While Freecam is flying, clicks would still attack, mine and place with your real player at a spot you cannot
    // see. These stop that; the hotbar and menus still work.

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void daedalus$noAttackInFreecam(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleRegistry.FREECAM.active()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void daedalus$noMiningInFreecam(boolean down, CallbackInfo ci) {
        if (ModuleRegistry.FREECAM.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void daedalus$noUseInFreecam(CallbackInfo ci) {
        if (ModuleRegistry.FREECAM.active()) {
            ci.cancel();
        }
    }
}
