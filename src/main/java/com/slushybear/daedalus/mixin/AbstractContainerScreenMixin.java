package com.slushybear.daedalus.mixin;

import com.slushybear.daedalus.module.visuals.CustomInventory;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    /** Vanilla draws the title and "Inventory" labels dark grey, which would vanish on a dark panel. */
    @ModifyArg(method = "extractLabels",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
            index = 4)
    private int daedalus$labelColor(int color) {
        return CustomInventory.labelColor((Screen) (Object) this, color);
    }
}
