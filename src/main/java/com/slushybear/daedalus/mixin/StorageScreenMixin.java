package com.slushybear.daedalus.mixin;

import com.slushybear.daedalus.module.visuals.CustomInventory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each storage screen draws its own background picture. With Custom inventory on, ours is drawn instead. */
@Mixin({ContainerScreen.class, ShulkerBoxScreen.class, HopperScreen.class, DispenserScreen.class})
public abstract class StorageScreenMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void daedalus$customBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                           CallbackInfo ci) {
        if (CustomInventory.drawBackground((AbstractContainerScreen<?>) (Object) this, graphics)) {
            ci.cancel();
        }
    }
}
