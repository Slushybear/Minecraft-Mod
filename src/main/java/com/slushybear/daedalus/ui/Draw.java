package com.slushybear.daedalus.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shape helpers. Vanilla can only fill plain rectangles, so rounded corners are built from thin rectangles, one row at a time. */
public final class Draw {
    private Draw() {}

    /** A filled rectangle with rounded corners. r is the corner radius in GUI pixels; 0 gives square corners. */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        // The straight middle section.
        g.fill(x, y + r, x + w, y + h - r, color);
        // The top and bottom rows near the corners, each pulled in a little more the closer it is to the edge.
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** A rounded box with a 1 pixel border, using the theme's corner radius. */
    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        int r = Theme.corner(h);
        round(g, x, y, w, h, r, border);
        round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }
}
