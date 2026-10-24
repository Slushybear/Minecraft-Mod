package com.slushybear.daedalus.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.slushybear.daedalus.module.WorldGizmos;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private GameRenderer gameRenderer;

    /**
     * Gizmos set to always-on-top (the ESP boxes and lines) are drawn in their own pass, which is handed the world fog.
     * Underwater that fog is thick and blue, so it tints the lines and washes them out with distance. This gives the
     * pass the game's "no fog" settings instead, so the ESP keeps its colours.
     */
    @ModifyVariable(method = "addAlwaysOnTopPass", at = @At("HEAD"), argsOnly = true)
    private GpuBufferSlice daedalus$noFogOnTop(GpuBufferSlice fog) {
        if (!WorldGizmos.active()) {
            return fog;
        }
        return ((GameRendererAccessor) gameRenderer).daedalus$fogRenderer().getBuffer(FogRenderer.FogMode.NONE);
    }
}
