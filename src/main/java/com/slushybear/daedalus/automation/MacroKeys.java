package com.slushybear.daedalus.automation;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Ten key slots. Players bind a key to each slot in the Controls menu, and a macro bound to the slot runs when it is pressed. */
public final class MacroKeys {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("daedalus", "macros"));
    private static final List<KeyMapping> KEYS = new ArrayList<>();

    private MacroKeys() {}

    public static void init() {
        for (int slot = 1; slot <= Limits.KEY_SLOTS; slot++) {
            KeyMapping key = new KeyMapping(
                    "key.daedalus.macro." + slot,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    CATEGORY);
            KEYS.add(key);
            KeyMappingHelper.registerKeyMapping(key);
        }
    }

    /** The key bound to a slot (1-10), as a Minecraft key name like "key.keyboard.g". */
    public static String keyName(int slot) {
        return KEYS.get(slot - 1).saveString();
    }

    /** Rebinds a slot from the menu. Minecraft saves it in options.txt, the same as setting it in Controls. */
    public static void setKey(int slot, InputConstants.Key key) {
        KEYS.get(slot - 1).setKey(key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    public static void poll() {
        for (int i = 0; i < KEYS.size(); i++) {
            if (KEYS.get(i).consumeClick()) {
                MacroManager.startBySlot(i + 1);
            }
        }
    }
}
