package com.slushybear.daedalus.module;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Keys are stored as Minecraft's key names, for example "key.keyboard.r" or "key.mouse.left".
 * These are the same names options.txt uses, so they survive saving and loading as plain strings.
 */
public final class KeyNames {
    public static final String NONE = "key.keyboard.unknown";

    private KeyNames() {}

    public static InputConstants.Key parse(String name) {
        if (name == null || name.isEmpty()) {
            return InputConstants.UNKNOWN;
        }
        try {
            return InputConstants.getKey(name);
        } catch (IllegalArgumentException e) {
            return InputConstants.UNKNOWN;
        }
    }

    public static boolean isNone(String name) {
        return parse(name).equals(InputConstants.UNKNOWN);
    }

    /** Short label for buttons: "R", "Right Shift", "LMB" or "None". */
    public static String display(String name) {
        InputConstants.Key key = parse(name);
        if (key.equals(InputConstants.UNKNOWN)) {
            return "None";
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return switch (key.getValue()) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
                default -> "Mouse " + (key.getValue() + 1);
            };
        }
        return key.getDisplayName().getString();
    }

    /** True while the key or mouse button is physically held down. */
    public static boolean isDown(Minecraft client, String name) {
        InputConstants.Key key = parse(name);
        if (key.equals(InputConstants.UNKNOWN)) {
            return false;
        }
        Window window = client.getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }
}
