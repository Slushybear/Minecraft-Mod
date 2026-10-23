package com.slushybear.daedalus.mixin;

import com.slushybear.daedalus.module.ModuleRegistry;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The lightmap decides how bright each light level looks. After vanilla fills in this frame's values, Fullbright
 * turns night vision up to full, which makes even light level 0 fully lit.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapExtractorMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void daedalus$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (ModuleRegistry.FULLBRIGHT.enabled()) {
            state.nightVisionEffectIntensity = 1;
            state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
            state.brightness = Math.max(state.brightness, 1);
            state.needsUpdate = true;
        }
    }
}
