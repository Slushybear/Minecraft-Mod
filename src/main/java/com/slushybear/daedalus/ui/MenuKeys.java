package com.slushybear.daedalus.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.slushybear.daedalus.module.KeyNames;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * The key that opens the menu (Right Shift by default). It is a normal Minecraft key mapping, so it shows up in
 * Options > Controls under "Daedalus", and Minecraft saves it in options.txt.
 */
public final class MenuKeys {
    /** The "Daedalus" section in Controls. Shared with module keys like Full Stop. */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("daedalus", "modules"));

    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.daedalus.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            CATEGORY);

    private MenuKeys() {}

    public static void init() {
        KeyMappingHelper.registerKeyMapping(OPEN_MENU);
    }

    /** Minecraft only counts key mapping presses while no screen is open, so this only fires in game. */
    public static void tick(Minecraft client) {
        while (OPEN_MENU.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new DaedalusScreen());
            }
        }
    }

    public static String keyName() {
        return OPEN_MENU.saveString();
    }

    /** Changes the open key from the menu. Clearing it puts it back to Right Shift, so the menu can always be opened. */
    public static void setKey(String name) {
        OPEN_MENU.setKey(KeyNames.isNone(name) ? OPEN_MENU.getDefaultKey() : KeyNames.parse(name));
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    public static void resetKey() {
        setKey(KeyNames.NONE);
    }
}
