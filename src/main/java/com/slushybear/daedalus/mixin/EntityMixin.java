package com.slushybear.daedalus.mixin;

import com.slushybear.daedalus.module.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /**
     * The mouse turns your player through Entity.turn. While Freecam or Freelook is on, the movement turns their
     * camera instead and your player keeps facing the same way.
     */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void daedalus$turnCamera(double dx, double dy, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (ModuleRegistry.FREECAM.active()) {
            ModuleRegistry.FREECAM.turn(dx, dy);
            ci.cancel();
        } else if (ModuleRegistry.FREELOOK.active()) {
            ModuleRegistry.FREELOOK.turn(dx, dy);
            ci.cancel();
        }
    }

    /** The glowing outline takes its colour from the team colour; Mob ESP's glow mode colours it by type. */
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void daedalus$glowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (ModuleRegistry.MOB_ESP.glows(self)) {
            cir.setReturnValue(ModuleRegistry.MOB_ESP.colorFor(self) & 0xFFFFFF);
        }
    }
}
