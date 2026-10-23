package com.slushybear.daedalus.mixin;

import com.slushybear.daedalus.module.ModuleRegistry;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Each tick KeyboardInput turns the movement keys into movement for your player. While Freecam is flying, that is
 * thrown away so your player stands still; Freecam reads the keys itself. Extending ClientInput lets this mixin
 * reach the protected moveVector field.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void daedalus$freezeForFreecam(CallbackInfo ci) {
        if (ModuleRegistry.FREECAM.active()) {
            keyPresses = Input.EMPTY;
            moveVector = Vec2.ZERO;
        }
    }
}
