package com.slushybear.daedalus.automation;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
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

    public static void poll() {
        for (int i = 0; i < KEYS.size(); i++) {
            if (KEYS.get(i).consumeClick()) {
                MacroManager.startBySlot(i + 1);
            }
        }
    }
}
