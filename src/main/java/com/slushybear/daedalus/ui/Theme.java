package com.slushybear.daedalus.ui;

import com.google.gson.JsonObject;
import java.util.List;

/**
 * The menu's colours. One accent colour drives everything: each surface is a fixed dark base colour mixed toward the
 * accent by the tint percentage, so picking red tints the whole menu red. All colours returned here are opaque ARGB ints,
 * which is what Minecraft's drawing calls take.
 */
public final class Theme {
    public record Preset(String name, int color) {}

    public static final List<Preset> PRESETS = List.of(
            new Preset("Daedalus", 0xa78bfa),
            new Preset("Crimson", 0xef4444),
            new Preset("Emerald", 0x34d399),
            new Preset("Ocean", 0x38bdf8),
            new Preset("Amber", 0xf59e0b),
            new Preset("Rose", 0xf472b6));

    public static final int MAX_TINT = 30;
    public static final int MAX_RADIUS = 14;
    public static final int MAX_DIM = 95;
    /** Menu scale choices: 0 is Auto, otherwise that many screen pixels per menu pixel. */
    public static final int MAX_SCALE = 4;

    private static final int DEFAULT_ACCENT = 0xa78bfa;
    private static final int DEFAULT_TEXT = 0xf2eefa;
    private static final int DEFAULT_TINT = 8;
    private static final int DEFAULT_RADIUS = 8;
    private static final int DEFAULT_DIM = 72;

    private static int accent = DEFAULT_ACCENT;
    private static int text = DEFAULT_TEXT;
    private static int tint = DEFAULT_TINT;
    private static int radius = DEFAULT_RADIUS;
    private static int dim = DEFAULT_DIM;
    private static boolean animations = true;
    private static int scale = 0;

    private Theme() {}

    // ---- Colours the menu draws with ----

    public static int accent() {
        return 0xFF000000 | accent;
    }

    public static int text() {
        return 0xFF000000 | text;
    }

    public static int bg() {
        return mix(accent, 0x120e19, tint / 100.0);
    }

    /** Sidebar, title bar and footer. */
    public static int side() {
        return mix(accent, 0x0c0911, tint / 100.0);
    }

    public static int panel() {
        return mix(accent, 0x16111e, tint / 100.0);
    }

    public static int card() {
        return mix(accent, 0x1e1729, tint / 100.0);
    }

    public static int cardSelected() {
        return mix(accent, 0x231b31, (tint + 7) / 100.0);
    }

    public static int line() {
        return mix(accent, 0x2a2236, 0.18);
    }

    public static int muted() {
        return mix(text, 0x4a4560, 0.52);
    }

    /** Track colour of a switch that is off: the text colour faintly over a card. */
    public static int toggleOff() {
        return mix(text, card(), 0.16);
    }

    /** Error messages and the delete confirm button. */
    public static int danger() {
        return 0xFFef4444;
    }

    /** Dark text drawn on top of the accent colour, for the selected segment. */
    public static int onAccent() {
        return 0xFF0B0812;
    }

    /** Corner radius in GUI pixels for a box of the given height. The setting is in mockup pixels, roughly twice a GUI pixel. */
    public static int corner(int height) {
        return Math.min(radius / 2, height / 2);
    }

    // ---- Colour maths ----

    /** Per channel: base + (color - base) * t. t = 0 gives base, t = 1 gives color. */
    public static int mix(int color, int base, double t) {
        int r = channel(color >> 16, base >> 16, t);
        int g = channel(color >> 8, base >> 8, t);
        int b = channel(color, base, t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int channel(int color, int base, double t) {
        int c = color & 0xFF;
        int bs = base & 0xFF;
        return (int) Math.round(bs + (c - bs) * t);
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return a << 24 | (color & 0xFFFFFF);
    }

    public static String hex(int color) {
        return String.format("#%06x", color & 0xFFFFFF);
    }

    /** Reads "#a78bfa" or "a78bfa". Returns -1 if the text is not a 6 digit hex colour. */
    public static int parseHex(String value) {
        String s = value.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (!s.matches("[0-9a-fA-F]{6}")) {
            return -1;
        }
        return Integer.parseInt(s, 16);
    }

    // ---- Settings ----

    public static int accentRgb() {
        return accent;
    }

    public static void setAccent(int rgb) {
        accent = rgb & 0xFFFFFF;
    }

    public static int textRgb() {
        return text;
    }

    public static void setText(int rgb) {
        text = rgb & 0xFFFFFF;
    }

    public static int tint() {
        return tint;
    }

    public static void setTint(int value) {
        tint = clamp(value, 0, MAX_TINT);
    }

    public static int radius() {
        return radius;
    }

    public static void setRadius(int value) {
        radius = clamp(value, 0, MAX_RADIUS);
    }

    public static int dim() {
        return dim;
    }

    public static void setDim(int value) {
        dim = clamp(value, 0, MAX_DIM);
    }

    public static boolean animations() {
        return animations;
    }

    public static void setAnimations(boolean value) {
        animations = value;
    }

    /** 0 means Auto. See DaedalusScreen.menuScale for how Auto picks a size. */
    public static int scale() {
        return scale;
    }

    public static void setScale(int value) {
        scale = clamp(value, 0, MAX_SCALE);
    }

    public static void reset() {
        accent = DEFAULT_ACCENT;
        text = DEFAULT_TEXT;
        tint = DEFAULT_TINT;
        radius = DEFAULT_RADIUS;
        dim = DEFAULT_DIM;
        animations = true;
        scale = 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- Saving ----

    public static JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("accent", hex(accent));
        json.addProperty("text", hex(text));
        json.addProperty("tint", tint);
        json.addProperty("radius", radius);
        json.addProperty("dim", dim);
        json.addProperty("animations", animations);
        json.addProperty("scale", scale);
        return json;
    }

    /** Reads what {@link #toJson()} wrote. Missing or broken values keep their current value. */
    public static void fromJson(JsonObject json) {
        if (json.has("accent") && parseHex(json.get("accent").getAsString()) >= 0) {
            setAccent(parseHex(json.get("accent").getAsString()));
        }
        if (json.has("text") && parseHex(json.get("text").getAsString()) >= 0) {
            setText(parseHex(json.get("text").getAsString()));
        }
        if (json.has("tint")) {
            setTint(json.get("tint").getAsInt());
        }
        if (json.has("radius")) {
            setRadius(json.get("radius").getAsInt());
        }
        if (json.has("dim")) {
            setDim(json.get("dim").getAsInt());
        }
        if (json.has("animations")) {
            setAnimations(json.get("animations").getAsBoolean());
        }
        if (json.has("scale")) {
            setScale(json.get("scale").getAsInt());
        }
    }
}
