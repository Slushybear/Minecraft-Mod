package com.slushybear.daedalus.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.slushybear.daedalus.module.KeyNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * The menu's widget toolkit: drawing helpers, controls, scrolling, dropdowns, toasts, and the keyboard and mouse state
 * behind them. Every colour and size comes from {@link Theme}.
 *
 * How it works: the whole menu is redrawn every frame. While drawing, each clickable control records its rectangle
 * and what a click should do (a {@link Hit}). When the mouse is clicked, the hits from the last frame are checked,
 * topmost first. All coordinates here are menu pixels; DaedalusScreen converts the mouse into them.
 */
final class Ui {
    static final int TOGGLE_W = Theme.SWITCH_W;
    static final int TOGGLE_H = Theme.SWITCH_H;
    static final int ROW_H = Theme.CONTROL_H;
    private static final int SCROLL_STEP = 18;
    private static final long TOAST_MS = 1800;

    /**
     * The bundled fonts, one version per menu scale: sans (Inter) for normal text, serif (Gelasio) for titles, and a
     * smaller serif for the wordmark. They are defined in assets/daedalus/font/. Each version draws its letters at
     * exactly the number of screen pixels the menu uses at that scale, so every letter pixel lands on one screen pixel
     * and text stays sharp instead of being stretched or squashed into a blur.
     */
    private static final Style[] SANS = fontsFor("sans");
    private static final Style[] SERIF = fontsFor("serif");
    private static final Style[] SERIF_SMALL = fontsFor("serif_small");

    private static Style[] fontsFor(String name) {
        Style[] styles = new Style[5];
        for (int scale = 1; scale <= 4; scale++) {
            Identifier id = Identifier.fromNamespaceAndPath("daedalus", name + "_" + scale);
            styles[scale] = Style.EMPTY.withFont(new FontDescription.Resource(id));
        }
        return styles;
    }

    /** What a click does. button is 0 for left, 1 for right. */
    interface Click {
        void on(int button, double mouseX, double mouseY);
    }

    /** fieldId is set when the hit belongs to a text field, so clicking inside the focused field keeps it focused. */
    private record Hit(int x1, int y1, int x2, int y2, Click click, String fieldId) {
        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    /** How far a scrolling area is scrolled. The owner keeps one per area so it survives between frames. */
    static final class Scroll {
        int offset;
        private int max;

        /** Call after drawing the content, with how tall it turned out and how tall the visible area is. */
        void fit(int contentHeight, int viewHeight) {
            max = Math.max(0, contentHeight - viewHeight);
            offset = Math.max(0, Math.min(offset, max));
        }

        void by(int amount) {
            offset = Math.max(0, Math.min(max, offset + amount));
        }

        void reset() {
            offset = 0;
        }
    }

    private record ScrollArea(int x1, int y1, int x2, int y2, Scroll scroll) {}

    /** A text field being typed in. */
    private static final class TextInput {
        final String id;
        final int maxLength;
        final IntPredicate allowed;
        final Consumer<String> onChange;
        final Consumer<String> onCommit;
        String text;
        int cursor;
        boolean allSelected;

        TextInput(String id, String text, int maxLength, IntPredicate allowed, Consumer<String> onChange,
                  Consumer<String> onCommit, boolean selectAll) {
            this.id = id;
            this.text = text;
            this.maxLength = maxLength;
            this.allowed = allowed;
            this.onChange = onChange;
            this.onCommit = onCommit;
            this.cursor = text.length();
            this.allSelected = selectAll && !text.isEmpty();
        }
    }

    /** A keybind button waiting for the next key. */
    private record Capture(String id, Consumer<String> onKey) {}

    /** A slider being dragged. */
    private record Drag(int x, int w, DoubleConsumer onFraction) {
        void update(double mouseX) {
            onFraction.accept(Math.max(0, Math.min(1, (mouseX - x) / w)));
        }
    }

    /** An open dropdown list, drawn on top of everything by {@link #drawPopup}. */
    private record Popup(String id, int x, int y, int w, List<String> options, String value, Consumer<String> onPick) {}

    static final IntPredicate ANY_TEXT = c -> true;
    static final IntPredicate WHOLE_NUMBER = c -> Character.isDigit(c) || c == '-';
    static final IntPredicate HEX = c -> Character.digit(c, 16) >= 0 || c == '#';

    final Font font;
    final Anim anim = new Anim();
    GuiGraphicsExtractor g;
    int mouseX;
    int mouseY;
    /** Screen pixels per menu pixel. Used to pick text scales and logo textures that stay sharp. */
    int pixelScale = 2;

    private final Minecraft minecraft;
    private final List<Hit> hits = new ArrayList<>();
    private final List<ScrollArea> scrollAreas = new ArrayList<>();
    private int clipX1, clipY1, clipX2, clipY2;

    private TextInput focus;
    private Capture capture;
    private Drag drag;
    private Popup popup;
    private boolean swallowNextChar;
    private String toastText;
    private int toastColor;
    private long toastAt;

    Ui(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.font = minecraft.font;
    }

    void beginFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int pixelScale) {
        this.g = graphics;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.pixelScale = pixelScale;
        hits.clear();
        scrollAreas.clear();
        anim.frame();
        resetClip();
    }

    void endFrame() {
        g = null;
    }

    // =====================================================================================================
    // Text and shapes
    // =====================================================================================================

    /** The font for normal text: the smooth sans at this menu scale, or Minecraft's own if Smooth text is off. */
    Style body() {
        return Theme.smoothText() ? SANS[fontScale()] : Style.EMPTY;
    }

    private int fontScale() {
        return Math.max(1, Math.min(4, pixelScale));
    }

    private Component styled(String text) {
        return Component.literal(text).withStyle(body());
    }

    /** How wide text is in the current font. Always measure with this, not font.width, so layout matches drawing. */
    int width(String text) {
        return font.width(styled(text));
    }

    /** The longest start of text that fits in maxWidth. */
    private String headByWidth(String text, int maxWidth) {
        return font.substrByWidth(FormattedText.of(text, body()), maxWidth).getString();
    }

    void text(String text, int x, int y, int color) {
        g.text(font, styled(text), x, y, color, false);
    }

    void text(FormattedCharSequence text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    void textRight(String text, int right, int y, int color) {
        text(text, right - width(text), y, color);
    }

    void textCentered(String text, int x, int w, int y, int color) {
        text(text, x + (w - width(text)) / 2, y, color);
    }

    /** Word-wrapped text. Returns the y below the last line. */
    int wrapped(String text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(FormattedText.of(text, body()), w)) {
            text(line, x, y, color);
            y += 10;
        }
        return y;
    }

    /** Small capitals with a pixel of space between letters, for section headings and labels like "PROFILE". */
    void spaced(String text, int x, int y, int color) {
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            text(c, x, y, color);
            x += width(c) + 1;
        }
    }

    int spacedWidth(String text) {
        return width(text) + Math.max(0, text.length() - 1);
    }

    /** A title in the bundled serif font, like a module name. */
    void heading(String text, int x, int y, int color) {
        g.text(font, Component.literal(text).withStyle(SERIF[fontScale()]), x, y, color, false);
    }

    int headingWidth(String text) {
        return font.width(Component.literal(text).withStyle(SERIF[fontScale()]));
    }

    /** The "Daedalus" wordmark in the smaller serif. */
    void wordmark(String text, int x, int y, int color) {
        g.text(font, Component.literal(text).withStyle(SERIF_SMALL[fontScale()]), x, y, color, false);
    }

    /** Cuts a title down to fit, with "...", measured in the serif font. */
    String truncateHeading(String text, int maxWidth) {
        if (headingWidth(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && headingWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    /** "Visuals / Block ESP" above a page title. child may be null. */
    void breadcrumb(String parent, String child, int x, int y) {
        text(parent, x, y, Theme.muted());
        if (child != null) {
            x += width(parent) + 4;
            text("/", x, y, Theme.pathLine());
            text(child, x + width("/") + 4, y, Theme.secondary());
        }
    }

    /**
     * A section heading: a small gold diamond, the name in spaced muted capitals, and a hairline running to the right
     * edge. Returns the y where the section's first row starts.
     */
    int section(String name, int x, int y, int w) {
        y += 9;
        diamond(x + 2.5f, y + 3.5f, Theme.SECTION_DIAMOND + 1, Theme.accent());
        String label = name.toUpperCase(Locale.ROOT);
        spaced(label, x + 9, y, Theme.muted());
        int lineX = x + 9 + spacedWidth(label) + 6;
        if (lineX < x + w) {
            g.fill(lineX, y + 3, x + w, y + 4, Theme.line());
        }
        return y + 13;
    }

    /** The y to draw text at so it looks centered in a box of height h starting at y. */
    static int textY(int y, int h) {
        return y + (h - 7) / 2;
    }

    String truncate(String text, int maxWidth) {
        if (width(text) <= maxWidth) {
            return text;
        }
        return headByWidth(text, Math.max(0, maxWidth - width("..."))) + "...";
    }

    void round(int x, int y, int w, int h, int r, int color) {
        Draw.round(g, x, y, w, h, r, color);
    }

    void box(int x, int y, int w, int h, int fill, int border) {
        Draw.box(g, x, y, w, h, fill, border);
    }

    void line(int x1, int y, int x2) {
        g.fill(x1, y, x2, y + 1, Theme.line());
    }

    void icon(String[] icon, int x, int y, int color) {
        Icons.draw(g, icon, x, y, color);
    }

    /**
     * A diamond: a square turned 45 degrees, centered on cx, cy. size is its width from tip to tip.
     * Drawn by rotating the drawing position, since Minecraft can only fill straight rectangles.
     */
    void diamond(float cx, float cy, float size, int color) {
        float half = size / 2f / (float) Math.sqrt(2);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((float) (Math.PI / 4));
        g.pose().scale(half, half);
        g.fill(-1, -1, 1, 1, color);
        g.pose().popMatrix();
    }

    /** A diamond outline: a filled diamond with a smaller one in the background colour on top. */
    void diamondOutline(float cx, float cy, float size, int color, int inside) {
        diamond(cx, cy, size, color);
        diamond(cx, cy, size - 2 * (float) Math.sqrt(2), inside);
    }

    /**
     * The Daedalus logo. It comes pre-shrunk in a few sizes (textures/gui/logo_N.png), and the one that matches the
     * menu scale is used, so every texture pixel lands on exactly one screen pixel and it stays sharp.
     */
    void logo(int x, int y, int size) {
        int texture = Math.max(1, Math.min(4, pixelScale)) * 16;
        Identifier id = Identifier.fromNamespaceAndPath("daedalus", "textures/gui/logo_" + texture + ".png");
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, size, size, texture, texture, texture, texture);
    }

    // =====================================================================================================
    // Hits, hover, clipping and scrolling
    // =====================================================================================================

    void hit(int x, int y, int w, int h, Click click) {
        hit(x, y, w, h, click, null);
    }

    private void hit(int x, int y, int w, int h, Click click, String fieldId) {
        int x1 = Math.max(x, clipX1);
        int y1 = Math.max(y, clipY1);
        int x2 = Math.min(x + w, clipX2);
        int y2 = Math.min(y + h, clipY2);
        if (x2 > x1 && y2 > y1) {
            hits.add(new Hit(x1, y1, x2, y2, click, fieldId));
        }
    }

    static Click left(Runnable action) {
        return (button, mx, my) -> {
            if (button == 0) {
                action.run();
            }
        };
    }

    boolean hover(int x, int y, int w, int h) {
        if (popup != null) {
            // While a dropdown is open, only it reacts to the mouse.
            return false;
        }
        return mouseX >= Math.max(x, clipX1) && mouseX < Math.min(x + w, clipX2)
                && mouseY >= Math.max(y, clipY1) && mouseY < Math.min(y + h, clipY2);
    }

    /** A 0 to 1 hover value that fades in and out. */
    float hoverFade(String id, int x, int y, int w, int h) {
        return anim.to("hover:" + id, hover(x, y, w, h) ? 1 : 0, Theme.HOVER_MS);
    }

    /**
     * Starts a scrolling area: content drawn until {@link #endScroll} is clipped to it, and the mouse wheel scrolls it.
     * Returns the y to start drawing content at.
     */
    int beginScroll(int x1, int y1, int x2, int y2, Scroll scroll) {
        scrollAreas.add(new ScrollArea(x1, y1, x2, y2, scroll));
        g.enableScissor(x1, y1, x2, y2);
        clipX1 = x1;
        clipY1 = y1;
        clipX2 = x2;
        clipY2 = y2;
        return y1 - scroll.offset;
    }

    /** Ends the scrolling area. contentEnd is the y just below the last thing drawn. Draws a scrollbar if needed. */
    void endScroll(Scroll scroll, int contentEnd) {
        int x2 = clipX2;
        int y1 = clipY1;
        int y2 = clipY2;
        g.disableScissor();
        resetClip();
        int view = y2 - y1;
        scroll.fit(contentEnd + scroll.offset - y1, view);
        if (scroll.max > 0) {
            int content = view + scroll.max;
            int barH = Math.max(12, view * view / content);
            int barY = y1 + (view - barH) * scroll.offset / scroll.max;
            g.fill(x2 - 3, barY, x2 - 2, barY + barH, Theme.pathLine());
        }
    }

    private void resetClip() {
        clipX1 = Integer.MIN_VALUE;
        clipY1 = Integer.MIN_VALUE;
        clipX2 = Integer.MAX_VALUE;
        clipY2 = Integer.MAX_VALUE;
    }

    // =====================================================================================================
    // Controls
    // =====================================================================================================

    /**
     * The flip switch that turns a module on and off: a rounded track with a diamond thumb that slides right when on.
     * Off is a dark track and a muted thumb; on is a gold border, a warm track and a light gold thumb.
     */
    void toggle(String id, int x, int y, boolean on, Runnable onClick) {
        float t = anim.to("toggle:" + id, on ? 1 : 0, Theme.SWITCH_MS);
        int w = Theme.SWITCH_W;
        int h = Theme.SWITCH_H;
        round(x, y, w, h, h / 2, Theme.mix(Theme.accent(), Theme.pathLine(), t));
        round(x + 1, y + 1, w - 2, h - 2, h / 2 - 1, Theme.mix(Theme.switchOnTrack(), Theme.toggleOff(), t));
        float left = x + h / 2f;
        float right = x + w - h / 2f;
        diamond(left + (right - left) * t, y + h / 2f, Theme.SWITCH_THUMB + 1,
                Theme.mix(Theme.accentLight(), Theme.muted(), t));
        hit(x - 2, y - 2, w + 4, h + 4, left(onClick));
    }

    /** The diamond checkbox: an outline that turns gold, with a filled gold diamond inside, when checked. */
    void checkbox(String id, int x, int y, boolean on, Runnable onClick) {
        int size = Theme.CHECK_SIZE;
        float t = anim.to("check:" + id, on ? 1 : 0, Theme.HOVER_MS);
        boolean hovered = hover(x - 2, y - 2, size + 4, size + 4);
        int border = Theme.mix(Theme.accent(), hovered ? Theme.secondary() : Theme.muted(), t);
        float c = size / 2f;
        diamondOutline(x + c, y + c, size + 2, border, Theme.bg());
        if (t > 0) {
            diamond(x + c, y + c, Theme.CHECK_FILL * t + 1, Theme.accent());
        }
        hit(x - 2, y - 2, size + 4, size + 4, left(onClick));
    }

    /** A label on the left with a checkbox on the right. Returns the y for the next row. */
    int toggleRow(String id, String label, int x, int y, int w, boolean on, Runnable onClick) {
        text(truncate(label, w - Theme.CHECK_SIZE - 8), x, y + 2, Theme.text());
        checkbox(id, x + w - Theme.CHECK_SIZE - 1, y + 1, on, onClick);
        return y + 17;
    }

    /**
     * A slider: a thin track, filled in gold up to a light gold diamond. Click or drag anywhere on it.
     * The value text is drawn by the caller.
     */
    void slider(int x, int y, int w, double fraction, DoubleConsumer onFraction) {
        int trackY = y + 3;
        int fill = (int) Math.round(fraction * w);
        g.fill(x, trackY, x + w, trackY + 2, Theme.pathLine());
        g.fill(x, trackY, x + fill, trackY + 2, Theme.accent());
        diamond(x + fill, trackY + 1, Theme.SLIDER_KNOB + 2, Theme.accentLight());
        hit(x - 4, y - 3, w + 8, 14, (button, mx, my) -> {
            if (button == 0) {
                drag = new Drag(x, w, onFraction);
                drag.update(mx);
            }
        });
    }

    /** A slider with its value to the right, in light gold. Placed by its right edge. */
    void sliderWithValue(int x, int y, int w, double fraction, String value, DoubleConsumer onFraction) {
        int valueW = Math.max(26, width(value) + 4);
        slider(x + 4, y, w - valueW - 8, fraction, onFraction);
        textRight(value, x + w, y, Theme.accentLight());
    }

    /** Label on the left, slider and value on the right. Returns the y for the next row. */
    int sliderRow(String label, String value, int x, int y, int w, double fraction, DoubleConsumer onFraction) {
        int controlW = Math.round(w * 0.55f);
        text(truncate(label, w - controlW - 6), x, y + 1, Theme.text());
        sliderWithValue(x + w - controlW, y + 1, controlW, fraction, value, onFraction);
        return y + 17;
    }

    /** A row of buttons where exactly one is picked. The picked one is raised, with light gold text. */
    void segmented(int x, int y, int w, int h, List<String> options, String value, Consumer<String> onPick) {
        box(x, y, w, h, Theme.bg(), Theme.line());
        int inner = w - 2;
        int n = options.size();
        for (int i = 0; i < n; i++) {
            String option = options.get(i);
            int bx = x + 1 + i * inner / n;
            int bw = (i + 1) * inner / n - i * inner / n;
            boolean on = option.equals(value);
            if (on) {
                round(bx, y + 1, bw, h - 2, Math.max(0, Theme.corner(h) - 1), Theme.card());
            }
            int color = on ? Theme.accentLight() : hover(bx, y + 1, bw, h - 2) ? Theme.text() : Theme.muted();
            textCentered(truncate(option, bw - 4), bx, bw, textY(y, h), color);
            hit(bx, y + 1, bw, h - 2, left(() -> onPick.accept(option)));
        }
    }

    /** How wide {@link #segmented} needs to be to fit every option without cutting any off. */
    int segmentedWidth(List<String> options) {
        int widest = 0;
        for (String option : options) {
            widest = Math.max(widest, width(option));
        }
        return options.size() * (widest + 12) + 2;
    }

    /**
     * Like {@link #segmented} but wraps onto more rows, for longer lists such as blocks or actions.
     * labels are what is shown; values are what onPick receives. Returns the y below the last row.
     */
    int chipGrid(int x, int y, int w, List<String> labels, List<String> values, String value, Consumer<String> onPick) {
        return chipGrid(x, y, w, labels, values, value::equals, onPick);
    }

    /** A chip grid where any number of chips can be on, for multi-choice settings. Returns the y below the last row. */
    int chipGrid(int x, int y, int w, List<String> options, Predicate<String> isOn, Consumer<String> onToggle) {
        return chipGrid(x, y, w, options, options, isOn, onToggle);
    }

    private int chipGrid(int x, int y, int w, List<String> labels, List<String> values, Predicate<String> isOn,
                         Consumer<String> onPick) {
        int h = 13;
        int gap = 3;
        int cx = x;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            String option = values.get(i);
            int cw = Math.min(w, width(label) + 10);
            if (cx + cw > x + w && cx > x) {
                cx = x;
                y += h + gap;
            }
            boolean on = isOn.test(option);
            boolean hovered = hover(cx, y, cw, h);
            box(cx, y, cw, h, on ? Theme.card() : Theme.bg(), on ? Theme.accent() : hovered ? Theme.pathLine() : Theme.line());
            int color = on ? Theme.accentLight() : hovered ? Theme.text() : Theme.muted();
            textCentered(truncate(label, cw - 4), cx, cw, textY(y, h), color);
            hit(cx, y, cw, h, left(() -> onPick.accept(option)));
            cx += cw + gap;
        }
        return y + h;
    }

    /**
     * A button showing a key, with a small keyboard icon. Click it and it reads "Press a key..." with a gold border;
     * the next key sets it, and Esc or Backspace clears it to None. Right, middle and side mouse buttons can be bound
     * too; a left click cancels, so a stray click never binds it. Placed by its right edge. Returns its width.
     */
    int bindButton(String id, int right, int y, String keyName, Consumer<String> onKey) {
        boolean listening = capture != null && capture.id().equals(id);
        String label = listening ? "Press a key..." : KeyNames.display(keyName);
        int w = Math.max(36, width(label) + Icons.SIZE + 14);
        int x = right - w;
        boolean hovered = hover(x, y, w, ROW_H);
        box(x, y, w, ROW_H, hovered ? Theme.hover() : Theme.card(), listening ? Theme.accent() : Theme.line());
        icon(Icons.KEYBOARD, x + 5, y + (ROW_H - Icons.SIZE) / 2, Theme.muted());
        text(label, x + 5 + Icons.SIZE + 4, textY(y, ROW_H), listening ? Theme.accentLight() : Theme.text());
        hit(x, y, w, ROW_H, left(() -> capture = new Capture(id, onKey)));
        return w;
    }

    /** A label on the left with a keybind button on the right. Returns the y for the next row. */
    int bindRow(String id, String label, int x, int y, int w, String keyName, Consumer<String> onKey) {
        text(truncate(label, w - 44), x, y + 3, Theme.text());
        bindButton(id, x + w, y, keyName, onKey);
        return y + 18;
    }

    /** A text field whose changes apply as you type. */
    void textField(String id, int x, int y, int w, int h, String value, String placeholder, int maxLength,
                   IntPredicate allowed, Consumer<String> onChange) {
        textField(id, x, y, w, h, value, placeholder, maxLength, allowed, onChange, null, false);
    }

    /**
     * A text field. onChange runs on every edit. If onCommit is given, it runs when you press Enter or click away,
     * and Esc throws the edit away instead. selectAll picks the whole text when the field is clicked, so typing replaces it.
     */
    void textField(String id, int x, int y, int w, int h, String value, String placeholder, int maxLength,
                   IntPredicate allowed, Consumer<String> onChange, Consumer<String> onCommit, boolean selectAll) {
        textField(id, x, y, w, h, value, placeholder, maxLength, allowed, onChange, onCommit, selectAll, 0, false);
    }

    /** A text field with a red border, for text that was not accepted. */
    void textFieldError(String id, int x, int y, int w, int h, String value, int maxLength, Consumer<String> onChange,
                        boolean error) {
        textField(id, x, y, w, h, value, "", maxLength, ANY_TEXT, onChange, null, false, 0, error);
    }

    /** The search box: a text field with a magnifier icon on the left. */
    void searchBox(String id, int x, int y, int w, int h, String value, String placeholder, Consumer<String> onChange) {
        textField(id, x, y, w, h, value, placeholder, 32, ANY_TEXT, onChange, null, false, Icons.SIZE + 4, false);
        icon(Icons.SEARCH, x + 5, y + (h - Icons.SIZE) / 2, isFocused(id) ? Theme.accent() : Theme.muted());
    }

    private void textField(String id, int x, int y, int w, int h, String value, String placeholder, int maxLength,
                           IntPredicate allowed, Consumer<String> onChange, Consumer<String> onCommit, boolean selectAll,
                           int padLeft, boolean error) {
        boolean focused = isFocused(id);
        int border = error ? Theme.danger() : focused ? Theme.accent() : Theme.line();
        box(x, y, w, h, Theme.card(), border);
        int textX = x + 5 + padLeft;
        int textW = w - 10 - padLeft;
        if (focused) {
            drawEditing(focus, textX, textY(y, h), textW);
        } else if (value.isEmpty()) {
            text(truncate(placeholder, textW), textX, textY(y, h), Theme.muted());
        } else {
            text(truncate(value, textW), textX, textY(y, h), Theme.text());
        }
        hit(x, y, w, h, left(() -> {
            if (!isFocused(id)) {
                focus(id, value, maxLength, allowed, onChange, onCommit, selectAll);
            }
        }), id);
    }

    /** Draws the text being edited, scrolled so the cursor is always visible, with a blinking cursor. */
    private void drawEditing(TextInput input, int x, int y, int maxWidth) {
        int start = 0;
        while (start < input.cursor && width(input.text.substring(start, input.cursor)) > maxWidth - 2) {
            start++;
        }
        String visible = headByWidth(input.text.substring(start), maxWidth);
        if (input.allSelected) {
            g.fill(x - 1, y - 1, x + width(visible) + 1, y + 8, Theme.withAlpha(Theme.accent(), 0.45f));
        }
        text(visible, x, y, Theme.text());
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = x + width(input.text.substring(start, input.cursor));
            g.fill(cursorX, y - 1, cursorX + 1, y + 8, Theme.accentLight());
        }
    }

    /** A clickable box with a centered label. */
    void chip(int x, int y, int w, int h, String label, Runnable onClick) {
        boolean hovered = hover(x, y, w, h);
        box(x, y, w, h, hovered ? Theme.hover() : Theme.card(), Theme.line());
        textCentered(truncate(label, w - 4), x, w, textY(y, h), Theme.text());
        hit(x, y, w, h, left(onClick));
    }

    /**
     * The main action of a page (accent) or a dangerous one (red): an outlined button in that colour. Placed by its
     * right edge. Returns its width.
     */
    int solidChip(int right, int y, String label, int color, Runnable onClick) {
        int w = width(label) + 14;
        int x = right - w;
        boolean hovered = hover(x, y, w, ROW_H);
        box(x, y, w, ROW_H, hovered ? Theme.hover() : Theme.card(), color);
        textCentered(label, x, w, textY(y, ROW_H), color == Theme.accent() ? Theme.accentLight() : color);
        hit(x, y, w, ROW_H, left(onClick));
        return w;
    }

    /** A normal chip placed by its right edge, sized to its label. Returns its width. */
    int chipRight(int right, int y, String label, Runnable onClick) {
        int w = width(label) + 14;
        chip(right - w, y, w, ROW_H, label, onClick);
        return w;
    }

    /** A small boxed icon button, for steppers and step lists. */
    void iconButton(int x, int y, int size, String[] icon, Runnable onClick) {
        boolean hovered = hover(x, y, size, size);
        box(x, y, size, size, hovered ? Theme.hover() : Theme.card(), Theme.line());
        int offset = (size - Icons.SIZE) / 2;
        icon(icon, x + offset, y + offset, hovered ? Theme.text() : Theme.muted());
        hit(x, y, size, size, left(onClick));
    }

    /** A bare icon that brightens on hover, like the reset, theme and close buttons. */
    void plainIcon(int x, int y, int size, String[] icon, Runnable onClick) {
        float hover = hoverFade("icon:" + x + ":" + y, x, y, size, size);
        int offset = (size - Icons.SIZE) / 2;
        icon(icon, x + offset, y + offset, Theme.mix(Theme.text(), Theme.muted(), hover));
        hit(x, y, size, size, left(onClick));
    }

    /** [-] value [+], placed by its right edge. Returns its width. */
    int stepper(int right, int y, String value, int valueWidth, Runnable onMinus, Runnable onPlus) {
        int size = ROW_H;
        int x = right - size * 2 - valueWidth - 4;
        iconButton(x, y, size, Icons.MINUS, onMinus);
        textCentered(value, x + size + 2, valueWidth, textY(y, size), Theme.accentLight());
        iconButton(right - size, y, size, Icons.PLUS, onPlus);
        return right - x;
    }

    // =====================================================================================================
    // Dropdowns
    // =====================================================================================================

    /**
     * A button showing the current choice and a small arrow. Clicking it opens a list of every option on top of the
     * menu; picking one closes it. The list itself is drawn by {@link #drawPopup} at the end of the frame.
     */
    void dropdown(String id, int x, int y, int w, int h, List<String> options, String value, Consumer<String> onPick) {
        boolean open = popup != null && popup.id().equals(id);
        boolean hovered = hover(x, y, w, h);
        box(x, y, w, h, hovered ? Theme.hover() : Theme.card(), open ? Theme.accent() : Theme.line());
        text(truncate(value, w - Icons.SIZE - 12), x + 5, textY(y, h), Theme.text());
        icon(Icons.CHEVRON, x + w - Icons.SIZE - 4, y + (h - Icons.SIZE) / 2 + 1, Theme.muted());
        hit(x, y, w, h, left(() -> popup = open ? null : new Popup(id, x, y + h + 2, w, options, value, onPick)));
    }

    /**
     * Draws the open dropdown list, if any, above everything else. Call last in the frame. The list opens upward when
     * there is no room below maxY. Clicking anywhere outside it closes it.
     */
    void drawPopup(int maxY) {
        if (popup == null) {
            return;
        }
        Popup p = popup;
        int itemH = 13;
        int h = p.options().size() * itemH + 4;
        int w = p.w();
        for (String option : p.options()) {
            w = Math.max(w, width(option) + 14);
        }
        int y = p.y() + h <= maxY ? p.y() : p.y() - h - ROW_H - 4;
        // Swallows clicks outside the list. Added before the items, so the items are on top of it.
        hits.add(new Hit(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                (button, mx, my) -> popup = null, null));
        box(p.x(), y, w, h, Theme.card(), Theme.line());
        for (int i = 0; i < p.options().size(); i++) {
            String option = p.options().get(i);
            int iy = y + 2 + i * itemH;
            boolean hovered = mouseX >= p.x() && mouseX < p.x() + w && mouseY >= iy && mouseY < iy + itemH;
            boolean on = option.equals(p.value());
            if (hovered) {
                g.fill(p.x() + 1, iy, p.x() + w - 1, iy + itemH, Theme.hover());
            }
            text(option, p.x() + 7, textY(iy, itemH), on ? Theme.accentLight() : hovered ? Theme.text() : Theme.secondary());
            hits.add(new Hit(p.x(), iy, p.x() + w, iy + itemH, left(() -> {
                popup = null;
                p.onPick().accept(option);
            }), null));
        }
    }

    boolean popupOpen() {
        return popup != null;
    }

    void closePopup() {
        popup = null;
    }

    // =====================================================================================================
    // Toast
    // =====================================================================================================

    void toast(String text, boolean on) {
        showToast(text, on ? Theme.accent() : Theme.muted());
    }

    void error(String text) {
        showToast(text, Theme.danger());
    }

    private void showToast(String text, int color) {
        toastText = text;
        toastColor = color;
        toastAt = System.currentTimeMillis();
    }

    /** Draws the current toast with its bottom-right corner at right, bottom. */
    void drawToast(int right, int bottom, int maxWidth) {
        if (toastText == null) {
            return;
        }
        long age = System.currentTimeMillis() - toastAt;
        if (age > TOAST_MS) {
            toastText = null;
            return;
        }
        float alpha = Theme.animations() ? Math.min(1f, Math.min(age, TOAST_MS - age) / 200f) : 1f;
        if (alpha < 0.05f) {
            return;
        }
        String text = truncate(toastText, maxWidth - 24);
        int w = width(text) + 22;
        int h = 17;
        int x = right - w;
        int y = bottom - h + Math.round((1 - alpha) * 4);
        int r = Theme.corner(h);
        round(x, y, w, h, r, Theme.withAlpha(Theme.line(), alpha));
        round(x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), Theme.withAlpha(Theme.card(), alpha));
        diamond(x + 9.5f, y + 8.5f, 5, Theme.withAlpha(toastColor, alpha));
        text(text, x + 16, y + 5, Theme.withAlpha(Theme.text(), alpha));
    }

    // =====================================================================================================
    // Input. Each returns true if the menu used the event.
    // =====================================================================================================

    boolean isFocused(String id) {
        return focus != null && focus.id.equals(id);
    }

    /** True while typing in a field or waiting for a keybind, when keys should not close the menu. */
    boolean isTyping() {
        return focus != null || capture != null;
    }

    void focus(String id, String value, int maxLength, IntPredicate allowed, Consumer<String> onChange,
               Consumer<String> onCommit, boolean selectAll) {
        commitFocus();
        focus = new TextInput(id, value, maxLength, allowed, onChange, onCommit, selectAll);
    }

    /** Leaves the current text field, keeping the edit. */
    void commitFocus() {
        TextInput done = focus;
        focus = null;
        if (done != null && done.onCommit != null) {
            done.onCommit.accept(done.text);
        }
    }

    void mouseClicked(double x, double y, int button) {
        if (capture != null) {
            Capture done = capture;
            capture = null;
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                done.onKey().accept(InputConstants.Type.MOUSE.getOrCreate(button).getName());
            }
            return;
        }
        // Later hits were drawn on top, so check them first.
        Hit target = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(x, y)) {
                target = hits.get(i);
                break;
            }
        }
        // Clicking anywhere except the focused field leaves it, keeping the edit. This happens before the click
        // runs, so for example a macro rename is saved before another macro gets selected.
        if (focus != null && (target == null || !focus.id.equals(target.fieldId()))) {
            commitFocus();
        }
        if (target != null) {
            target.click().on(button, x, y);
        }
    }

    boolean mouseDragged(double x) {
        if (drag != null) {
            drag.update(x);
            return true;
        }
        return false;
    }

    void mouseReleased() {
        drag = null;
    }

    void mouseScrolled(double x, double y, double amount) {
        if (popup != null) {
            return;
        }
        for (int i = scrollAreas.size() - 1; i >= 0; i--) {
            ScrollArea area = scrollAreas.get(i);
            if (x >= area.x1() && x < area.x2() && y >= area.y1() && y < area.y2()) {
                area.scroll().by((int) Math.round(-amount * SCROLL_STEP));
                return;
            }
        }
    }

    boolean keyPressed(KeyEvent event) {
        swallowNextChar = false;
        if (capture != null) {
            Capture done = capture;
            capture = null;
            // The same key press also arrives as a typed character next; drop it so binding "/" does not open search.
            swallowNextChar = true;
            boolean clear = event.isEscape() || event.key() == GLFW.GLFW_KEY_BACKSPACE;
            done.onKey().accept(clear ? KeyNames.NONE : InputConstants.getKey(event).getName());
            return true;
        }
        if (popup != null && event.isEscape()) {
            popup = null;
            return true;
        }
        if (focus == null) {
            return false;
        }
        TextInput in = focus;
        int key = event.key();
        if (event.isEscape()) {
            // With a commit step, Esc throws the edit away. Without one, the edit is already applied.
            focus = null;
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commitFocus();
        } else if (event.isSelectAll()) {
            in.allSelected = !in.text.isEmpty();
            in.cursor = in.text.length();
        } else if (event.isPaste()) {
            insert(in, minecraft.keyboardHandler.getClipboard().replaceAll("[\\r\\n]", ""));
        } else if (event.isCopy()) {
            minecraft.keyboardHandler.setClipboard(in.text);
        } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (in.allSelected) {
                edit(in, "", 0);
            } else if (in.cursor > 0) {
                int from = event.hasControlDown() ? 0 : in.cursor - 1;
                edit(in, in.text.substring(0, from) + in.text.substring(in.cursor), from);
            }
        } else if (key == GLFW.GLFW_KEY_DELETE) {
            if (in.allSelected) {
                edit(in, "", 0);
            } else if (in.cursor < in.text.length()) {
                edit(in, in.text.substring(0, in.cursor) + in.text.substring(in.cursor + 1), in.cursor);
            }
        } else if (key == GLFW.GLFW_KEY_LEFT) {
            in.cursor = in.allSelected ? 0 : Math.max(0, in.cursor - 1);
            in.allSelected = false;
        } else if (key == GLFW.GLFW_KEY_RIGHT) {
            in.cursor = in.allSelected ? in.text.length() : Math.min(in.text.length(), in.cursor + 1);
            in.allSelected = false;
        } else if (key == GLFW.GLFW_KEY_HOME) {
            in.cursor = 0;
            in.allSelected = false;
        } else if (key == GLFW.GLFW_KEY_END) {
            in.cursor = in.text.length();
            in.allSelected = false;
        }
        // Every key is used while typing, so letters like E or the menu key never reach the game or close the menu.
        return true;
    }

    boolean charTyped(CharacterEvent event) {
        if (capture != null || swallowNextChar) {
            swallowNextChar = false;
            return true;
        }
        if (focus == null) {
            return false;
        }
        if (event.isAllowedChatCharacter() && focus.allowed.test(event.codepoint())) {
            insert(focus, event.codepointAsString());
        }
        return true;
    }

    private void insert(TextInput in, String s) {
        String base = in.allSelected ? "" : in.text;
        int at = in.allSelected ? 0 : in.cursor;
        StringBuilder filtered = new StringBuilder();
        s.codePoints().filter(in.allowed).forEach(filtered::appendCodePoint);
        int room = in.maxLength - base.length();
        if (room <= 0 || filtered.isEmpty()) {
            return;
        }
        String add = filtered.length() > room ? filtered.substring(0, room) : filtered.toString();
        edit(in, base.substring(0, at) + add + base.substring(at), at + add.length());
    }

    private void edit(TextInput in, String text, int cursor) {
        in.text = text;
        in.cursor = cursor;
        in.allSelected = false;
        in.onChange.accept(text);
    }
}
