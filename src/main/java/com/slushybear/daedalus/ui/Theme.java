package com.slushybear.daedalus.ui;

import com.google.gson.JsonObject;
import java.util.List;

/**
 * Every colour and size the menu uses, defined once. The screen and widgets read from here instead of hardcoding values.
 * Colours are opaque ARGB ints (0xAARRGGBB), which is what Minecraft's drawing calls take. Sizes are menu pixels.
 *
 * The look is charcoal surfaces with one accent colour (muted champagne gold by default). The accent is only for things
 * that are on, selected or focused. It can be changed at runtime, and the tint setting mixes a little of it into every
 * surface, so read colours through these methods every frame instead of keeping them.
 */
public final class Theme {
    public record Preset(String name, int color) {}

    public static final List<Preset> PRESETS = List.of(
            new Preset("Gold", 0xB8975A),
            new Preset("Silver", 0xA9ADB4),
            new Preset("Jade", 0x6FA889),
            new Preset("Azure", 0x6A9AC4),
            new Preset("Violet", 0x9A86C8),
            new Preset("Rose", 0xC47F8E));

    public static final int MAX_TINT = 30;
    public static final int MAX_RADIUS = 14;
    public static final int MAX_DIM = 95;
    /** Menu scale choices: 0 is Auto, otherwise that many screen pixels per menu pixel. */
    public static final int MAX_SCALE = 4;

    // ---- Sizes, in menu pixels ----

    public static final int MAX_WINDOW_W = 560;
    public static final int MAX_WINDOW_H = 330;
    /** Share of the window the sidebar takes; the content panel gets the rest. */
    public static final float SIDEBAR_FRACTION = 0.27f;
    public static final int CONTENT_PAD_X = 13;
    public static final int CONTENT_PAD_Y = 10;

    public static final int SIDEBAR_HEADER_H = 30;
    public static final int SIDEBAR_FOOTER_H = 30;
    public static final int SEARCH_H = 15;
    public static final int CATEGORY_H = 15;
    public static final int BRANCH_ITEM_H = 13;
    /** x of the labyrinth path line, from the sidebar's left edge. */
    public static final int BRANCH_INDENT = 11;
    /** Horizontal tick from the path line to each feature. */
    public static final int PATH_TICK = 6;
    /** Tip-to-tip width of the gold diamond on the path line at the selected feature. */
    public static final int PATH_MARKER = 6;
    public static final int ENABLED_DOT = 3;
    public static final int LOGO_SIZE = 16;

    public static final int SECTION_DIAMOND = 4;
    public static final int ROW_PAD_Y = 5;
    /** Slider and text field width, as a share of the row. */
    public static final float CONTROL_COLUMN = 0.46f;

    public static final int CHECK_SIZE = 10;
    public static final int CHECK_FILL = 5;
    public static final int SLIDER_KNOB = 7;
    public static final int SWITCH_W = 20;
    public static final int SWITCH_H = 10;
    public static final int SWITCH_THUMB = 5;
    public static final int CONTROL_H = 14;
    /** Choice settings show every option as buttons up to this many; more becomes a dropdown. */
    public static final int MAX_SEGMENTS = 4;

    // ---- Motion, in milliseconds ----

    public static final int HOVER_MS = 100;
    public static final int SWITCH_MS = 140;
    public static final int ACCORDION_MS = 120;

    // ---- The charcoal palette. Surfaces get the accent mixed in by the tint setting. ----

    private static final int WINDOW_BG = 0x0A0A09;
    private static final int PANEL_BG = 0x0F0F0E;
    private static final int RAISED_BG = 0x161613;
    private static final int HOVER_BG = 0x1B1A17;
    private static final int BORDER = 0x272520;
    private static final int PATH_LINE = 0x3A362C;
    private static final int ROW_DIVIDER = 0x191815;
    private static final int SWITCH_OFF = 0x24231F;

    private static final int DEFAULT_ACCENT = 0xB8975A;
    private static final int DEFAULT_TEXT = 0xEFEBE2;
    private static final int DEFAULT_TINT = 0;
    private static final int DEFAULT_RADIUS = 6;
    private static final int DEFAULT_DIM = 60;
    /** Bumped when the default look changes, so colours saved by an older look are dropped. */
    private static final int STYLE = 2;

    private static int accent = DEFAULT_ACCENT;
    private static int text = DEFAULT_TEXT;
    private static int tint = DEFAULT_TINT;
    private static int radius = DEFAULT_RADIUS;
    private static int dim = DEFAULT_DIM;
    private static boolean animations = true;
    private static boolean smoothText = true;
    private static int scale = 0;

    private Theme() {}

    // ---- Accent ----

    /** Borders of focused things, filled diamonds, slider fill, counts. */
    public static int accent() {
        return 0xFF000000 | accent;
    }

    /** Selected text, slider thumb, values, the switch thumb when on. */
    public static int accentLight() {
        return mix(0xFFFFFF, accent, 0.28);
    }

    /** The warm dark track of a switch that is on. */
    public static int switchOnTrack() {
        return mix(accent, WINDOW_BG, 0.2);
    }

    /** Dark text drawn on top of the accent colour. */
    public static int onAccent() {
        return 0xFF000000 | WINDOW_BG;
    }

    // ---- Surfaces ----

    /** The content panel. */
    public static int bg() {
        return surface(WINDOW_BG);
    }

    /** The sidebar. */
    public static int side() {
        return surface(PANEL_BG);
    }

    /** A second panel inside the content area, like the macro editor's right side. */
    public static int panel() {
        return surface(PANEL_BG);
    }

    /** Inputs, buttons and the active segment. */
    public static int card() {
        return surface(RAISED_BG);
    }

    public static int hover() {
        return surface(HOVER_BG);
    }

    /** A card that is selected. */
    public static int cardSelected() {
        return surface(HOVER_BG);
    }

    /** Panel and input borders, and hairlines. */
    public static int line() {
        return surface(BORDER);
    }

    /** The sidebar's path line and the slider track. */
    public static int pathLine() {
        return surface(PATH_LINE);
    }

    /** The faint line between setting rows. */
    public static int rowDivider() {
        return surface(ROW_DIVIDER);
    }

    /** Track of a switch that is off. */
    public static int toggleOff() {
        return surface(SWITCH_OFF);
    }

    private static int surface(int base) {
        return mix(accent, base, tint / 100.0);
    }

    // ---- Text ----

    /** Labels. */
    public static int text() {
        return 0xFF000000 | text;
    }

    /** Descriptions and unselected sidebar items. */
    public static int secondary() {
        return mix(text, WINDOW_BG, 0.63);
    }

    /** Section headings, counts and icons. */
    public static int muted() {
        return mix(text, WINDOW_BG, 0.42);
    }

    /** Invalid text input and the delete confirm button. */
    public static int danger() {
        return 0xFFD9776B;
    }

    /** Corner radius for a control of the given height. The setting is in mockup pixels, roughly twice a menu pixel. */
    public static int corner(int height) {
        return Math.min(radius / 2, height / 2);
    }

    /** Corner radius of the menu window. */
    public static int windowCorner() {
        return (radius + 4) / 2;
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

    /** Reads "#b8975a" or "b8975a". Returns -1 if the text is not a 6 digit hex colour. */
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

    /** True draws menu text in the bundled sans font; false uses Minecraft's pixel font. */
    public static boolean smoothText() {
        return smoothText;
    }

    public static void setSmoothText(boolean value) {
        smoothText = value;
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
        smoothText = true;
        scale = 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- Saving ----

    public static JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("style", STYLE);
        json.addProperty("accent", hex(accent));
        json.addProperty("text", hex(text));
        json.addProperty("tint", tint);
        json.addProperty("radius", radius);
        json.addProperty("dim", dim);
        json.addProperty("animations", animations);
        json.addProperty("smoothText", smoothText);
        json.addProperty("scale", scale);
        return json;
    }

    /**
     * Reads what {@link #toJson()} wrote. Missing or broken values keep their current value. Colours saved by an
     * older look (no matching "style") are skipped, so updating the mod brings in the new colours.
     */
    public static void fromJson(JsonObject json) {
        boolean sameStyle = json.has("style") && json.get("style").getAsInt() == STYLE;
        if (sameStyle) {
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
        }
        if (json.has("dim")) {
            setDim(json.get("dim").getAsInt());
        }
        if (json.has("animations")) {
            setAnimations(json.get("animations").getAsBoolean());
        }
        if (json.has("smoothText")) {
            setSmoothText(json.get("smoothText").getAsBoolean());
        }
        if (json.has("scale")) {
            setScale(json.get("scale").getAsInt());
        }
    }
}
