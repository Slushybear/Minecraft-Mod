package com.slushybear.daedalus.module;

import com.mojang.blaze3d.platform.InputConstants;
import com.slushybear.daedalus.ui.MenuKeys;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Press the key to zero your velocity instantly. */
public final class FullStopModule extends Module {
    private final KeyMapping key = new KeyMapping(
            "key.daedalus.full_stop",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            MenuKeys.CATEGORY);

    public FullStopModule() {
        super("Full Stop", "Press a key, stop dead", Category.MISC, true);
    }

    @Override
    public void init() {
        KeyMappingHelper.registerKeyMapping(key);
    }

    @Override
    public void tick(Minecraft client) {
        if (key.consumeClick() && client.player != null) {
            client.player.setDeltaMovement(Vec3.ZERO);
        }
    }
}
