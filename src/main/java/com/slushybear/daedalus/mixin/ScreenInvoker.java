package com.slushybear.daedalus.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Custom inventory call the protected method that blurs the world behind a screen. */
@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("extractBlurredBackground")
    void daedalus$extractBlurredBackground(GuiGraphicsExtractor graphics);
}
