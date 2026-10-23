package com.slushybear.daedalus.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.slushybear.daedalus.module.KeyNames;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * The menu's widget toolkit: drawing helpers, controls, scrolling, toasts, and the keyboard and mouse state behind them.
 *
 * How it works: the whole menu is redrawn every frame. While drawing, each clickable control records its rectangle
 * and what a click should do (a {@link Hit}). When the mouse is clicked, the hits from the last frame are checked,
 * topmost first. All coordinates here are menu pixels; DaedalusScreen converts the mouse into them.
 */
final class Ui {
    static final int TOGGLE_W = 18;
    static final int TOGGLE_H = 10;
    static final int ROW_H = 14;
    private static final int SCROLL_STEP = 18;
    private static final long TOAST_MS = 1800;

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
            onFraction.accept(Math.max(0, Math.min(1, (mouseX - x - 4) / (w - 8))));
        }
    }

    static final IntPredicate ANY_TEXT = c -> true;
    static final IntPredicate WHOLE_NUMBER = c -> Character.isDigit(c) || c == '-';
    static final IntPredicate HEX = c -> Character.digit(c, 16) >= 0 || c == '#';

    final Font font;
    final Anim anim = new Anim();
    GuiGraphicsExtractor g;
    int mouseX;
    int mouseY;
    /** Screen pixels per menu pixel. Used to pick text scales that stay sharp. */
    int pixelScale = 2;

    private final Minecraft minecraft;
    private final List<Hit> hits = new ArrayList<>();
    private final List<ScrollArea> scrollAreas = new ArrayList<>();
    private int clipX1, clipY1, clipX2, clipY2;

    private TextInput focus;
    private Capture capture;
    private Drag drag;
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

    void text(String text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    void text(FormattedCharSequence text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    void textRight(String text, int right, int y, int color) {
        text(text, right - font.width(text), y, color);
    }

    void textCentered(String text, int x, int w, int y, int color) {
        text(text, x + (w - font.width(text)) / 2, y, color);
    }

    /** Word-wrapped text. Returns the y below the last line. */
    int wrapped(String text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(FormattedText.of(text), w)) {
            text(line, x, y, color);
            y += 10;
        }
        return y;
    }

    /**
     * Big heading text. It is drawn one screen pixel bigger per font pixel than normal text (for example 3 instead
     * of 2), which keeps the pixel font sharp; a scale like 1.5 would make some letters lumpy.
     */
    void heading(String text, int x, int y, int color) {
        float scale = (pixelScale + 1) / (float) pixelScale;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** The y to draw text at so it looks centered in a box of height h starting at y. */
    static int textY(int y, int h) {
        return y + (h - 7) / 2;
    }

    String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
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
        return mouseX >= Math.max(x, clipX1) && mouseX < Math.min(x + w, clipX2)
                && mouseY >= Math.max(y, clipY1) && mouseY < Math.min(y + h, clipY2);
    }

    /** A 0 to 1 hover value that fades in and out. */
    float hoverFade(String id, int x, int y, int w, int h) {
        return anim.to("hover:" + id, hover(x, y, w, h) ? 1 : 0);
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
            round(x2 - 3, barY, 2, barH, 1, Theme.mix(Theme.accent(), Theme.line(), 0.35));
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

    void toggle(String id, int x, int y, boolean on, Runnable onClick) {
        float t = anim.to("toggle:" + id, on ? 1 : 0);
        round(x, y, TOGGLE_W, TOGGLE_H, TOGGLE_H / 2, Theme.mix(Theme.accent(), Theme.toggleOff(), t));
        int knobX = x + 2 + Math.round(t * (TOGGLE_W - 10));
        round(knobX, y + 2, 6, 6, 3, 0xFFFFFFFF);
        hit(x - 2, y - 2, TOGGLE_W + 4, TOGGLE_H + 4, left(onClick));
    }

    /** A label on the left with a toggle on the right. Returns the y for the next row. */
    int toggleRow(String id, String label, int x, int y, int w, boolean on, Runnable onClick) {
        text(truncate(label, w - TOGGLE_W - 6), x, y + 2, Theme.text());
        toggle(id, x + w - TOGGLE_W, y + 1, on, onClick);
        return y + 17;
    }

    void slider(int x, int y, int w, double fraction, DoubleConsumer onFraction) {
        int knobX = x + (int) Math.round(fraction * (w - 8));
        round(x, y + 2, w, 4, 2, Theme.toggleOff());
        round(x, y + 2, knobX - x + 4, 4, 2, Theme.accent());
        round(knobX, y, 8, 8, 4, Theme.accent());
        round(knobX + 2, y + 2, 4, 4, 2, Theme.text());
        hit(x, y - 3, w, 14, (button, mx, my) -> {
            if (button == 0) {
                drag = new Drag(x, w, onFraction);
                drag.update(mx);
            }
        });
    }

    /** Label and value on one line, slider below. Returns the y for the next row. */
    int sliderRow(String label, String value, int x, int y, int w, double fraction, DoubleConsumer onFraction) {
        text(truncate(label, w - font.width(value) - 6), x, y, Theme.text());
        textRight(value, x + w, y, Theme.muted());
        slider(x, y + 12, w, fraction, onFraction);
        return y + 27;
    }

    /** A row of buttons where exactly one is picked. */
    void segmented(int x, int y, int w, int h, List<String> options, String value, Consumer<String> onPick) {
        box(x, y, w, h, Theme.card(), Theme.line());
        int inner = w - 4;
        int n = options.size();
        for (int i = 0; i < n; i++) {
            String option = options.get(i);
            int bx = x + 2 + i * inner / n;
            int bw = (i + 1) * inner / n - i * inner / n - (i < n - 1 ? 1 : 0);
            boolean on = option.equals(value);
            if (on) {
                round(bx, y + 2, bw, h - 4, Math.max(0, Theme.corner(h) - 1), Theme.accent());
            }
            int color = on ? Theme.onAccent() : hover(bx, y + 2, bw, h - 4) ? Theme.text() : Theme.muted();
            String label = truncate(option, bw - 2);
            textCentered(label, bx, bw, textY(y, h), color);
            hit(bx, y + 2, bw, h - 4, left(() -> onPick.accept(option)));
        }
    }

    /**
     * Like {@link #segmented} but wraps onto more rows, for longer lists such as blocks or actions.
     * labels are what is shown; values are what onPick receives. Returns the y below the last row.
     */
    int chipGrid(int x, int y, int w, List<String> labels, List<String> values, String value, Consumer<String> onPick) {
        int h = 13;
        int gap = 3;
        int cx = x;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            String option = values.get(i);
            int cw = Math.min(w, font.width(label) + 10);
            if (cx + cw > x + w && cx > x) {
                cx = x;
                y += h + gap;
            }
            boolean on = option.equals(value);
            boolean hovered = hover(cx, y, cw, h);
            if (on) {
                round(cx, y, cw, h, Theme.corner(h), Theme.accent());
            } else {
                box(cx, y, cw, h, Theme.card(), hovered ? Theme.accent() : Theme.line());
            }
            int color = on ? Theme.onAccent() : hovered ? Theme.text() : Theme.muted();
            textCentered(truncate(label, cw - 4), cx, cw, textY(y, h), color);
            hit(cx, y, cw, h, left(() -> onPick.accept(option)));
            cx += cw + gap;
        }
        return y + h;
    }

    /**
     * A button showing a key. Click it, then press a key to set it. Esc or Backspace clears it to None.
     * Right, middle and side mouse buttons can be bound too; a left click cancels, so a stray click never binds it.
     * Returns the button's width.
     */
    int bindButton(String id, int right, int y, String keyName, Consumer<String> onKey) {
        boolean listening = capture != null && capture.id().equals(id);
        String label = listening ? "Press a key" : KeyNames.display(keyName);
        int w = Math.max(36, font.width(label) + 12);
        int x = right - w;
        boolean hovered = hover(x, y, w, ROW_H);
        box(x, y, w, ROW_H, Theme.card(), listening || hovered ? Theme.accent() : Theme.line());
        textCentered(label, x, w, textY(y, ROW_H), listening ? Theme.accent() : Theme.text());
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
        textField(id, x, y, w, h, value, placeholder, maxLength, allowed, onChange, onCommit, selectAll, 0);
    }

    /** The search box in the title bar: a text field with a magnifier icon on the left. */
    void searchBox(String id, int x, int y, int w, int h, String value, Consumer<String> onChange) {
        textField(id, x, y, w, h, value, "Search modules", 32, ANY_TEXT, onChange, null, false, Icons.SIZE + 4);
        icon(Icons.SEARCH, x + 5, y + (h - Icons.SIZE) / 2, isFocused(id) ? Theme.accent() : Theme.muted());
    }

    private void textField(String id, int x, int y, int w, int h, String value, String placeholder, int maxLength,
                           IntPredicate allowed, Consumer<String> onChange, Consumer<String> onCommit, boolean selectAll,
                           int padLeft) {
        boolean focused = isFocused(id);
        box(x, y, w, h, Theme.card(), focused ? Theme.accent() : Theme.line());
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
        while (start < input.cursor && font.width(input.text.substring(start, input.cursor)) > maxWidth - 2) {
            start++;
        }
        String visible = font.plainSubstrByWidth(input.text.substring(start), maxWidth);
        if (input.allSelected) {
            g.fill(x - 1, y - 1, x + font.width(visible) + 1, y + 8, Theme.withAlpha(Theme.accent(), 0.45f));
        }
        text(visible, x, y, Theme.text());
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = x + font.width(input.text.substring(start, input.cursor));
            g.fill(cursorX, y - 1, cursorX + 1, y + 8, Theme.accent());
        }
    }

    /** A clickable box with a centered label. */
    void chip(int x, int y, int w, int h, String label, Runnable onClick) {
        boolean hovered = hover(x, y, w, h);
        box(x, y, w, h, Theme.card(), hovered ? Theme.accent() : Theme.line());
        textCentered(truncate(label, w - 4), x, w, textY(y, h), hovered ? Theme.text() : Theme.muted());
        hit(x, y, w, h, left(onClick));
    }

    /** A chip filled with a solid colour, for the main action (accent) or a dangerous one (red). Returns its width. */
    int solidChip(int right, int y, String label, int color, Runnable onClick) {
        int w = font.width(label) + 14;
        int x = right - w;
        boolean hovered = hover(x, y, w, ROW_H);
        round(x, y, w, ROW_H, Theme.corner(ROW_H), hovered ? Theme.mix(0xFFFFFF, color, 0.15) : color);
        textCentered(label, x, w, textY(y, ROW_H), Theme.onAccent());
        hit(x, y, w, ROW_H, left(onClick));
        return w;
    }

    /** A normal chip placed by its right edge, sized to its label. Returns its width. */
    int chipRight(int right, int y, String label, Runnable onClick) {
        int w = font.width(label) + 14;
        chip(right - w, y, w, ROW_H, label, onClick);
        return w;
    }

    void iconButton(int x, int y, int size, String[] icon, Runnable onClick) {
        boolean hovered = hover(x, y, size, size);
        box(x, y, size, size, Theme.card(), hovered ? Theme.accent() : Theme.line());
        int offset = (size - Icons.SIZE) / 2;
        icon(icon, x + offset, y + offset, hovered ? Theme.text() : Theme.muted());
        hit(x, y, size, size, left(onClick));
    }

    /** [-] value [+], placed by its right edge. Returns its width. */
    int stepper(int right, int y, String value, int valueWidth, Runnable onMinus, Runnable onPlus) {
        int size = ROW_H;
        int x = right - size * 2 - valueWidth - 4;
        iconButton(x, y, size, Icons.MINUS, onMinus);
        textCentered(value, x + size + 2, valueWidth, textY(y, size), Theme.text());
        iconButton(right - size, y, size, Icons.PLUS, onPlus);
        return right - x;
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
        int w = font.width(text) + 22;
        int h = 17;
        int x = right - w;
        int y = bottom - h + Math.round((1 - alpha) * 4);
        int r = Theme.corner(h);
        round(x, y, w, h, r, Theme.withAlpha(Theme.line(), alpha));
        round(x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), Theme.withAlpha(Theme.cardSelected(), alpha));
        round(x + 7, y + 6, 5, 5, 2, Theme.withAlpha(toastColor, alpha));
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
