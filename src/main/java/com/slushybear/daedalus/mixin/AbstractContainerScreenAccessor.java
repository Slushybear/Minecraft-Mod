package com.slushybear.daedalus.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads where a container screen's panel sits. These fields are protected, so Custom inventory needs this to see them. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int daedalus$leftPos();

    @Accessor("topPos")
    int daedalus$topPos();

    @Accessor("imageWidth")
    int daedalus$imageWidth();

    @Accessor("imageHeight")
    int daedalus$imageHeight();
}
