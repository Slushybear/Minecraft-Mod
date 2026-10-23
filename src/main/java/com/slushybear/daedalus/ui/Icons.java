package com.slushybear.daedalus.ui;

import com.slushybear.daedalus.module.Category;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small 7x7 pixel icons, drawn with plain rectangles so they stay sharp at any menu scale.
 * Each icon is 7 rows of 7 characters; '#' is a filled pixel. Edit the pictures here to change an icon.
 */
final class Icons {
    static final int SIZE = 7;

    static final String[] SWORD = {
            ".....##",
            "....###",
            "...###.",
            "#.###..",
            ".##....",
            ".##....",
            "#..#...",
    };
    static final String[] PICKAXE = {
            ".#####.",
            "#..#..#",
            "...#...",
            "...#...",
            "...#...",
            "...#...",
            "...#...",
    };
    static final String[] WHEAT = {
            "#..#..#",
            ".#.#.#.",
            "#..#..#",
            ".#.#.#.",
            "..###..",
            "...#...",
            "...#...",
    };
    static final String[] PLAY = {
            ".#.....",
            ".##....",
            ".###...",
            ".####..",
            ".###...",
            ".##....",
            ".#.....",
    };
    static final String[] DOTS = {
            ".......",
            ".......",
            ".......",
            "##.#.##",
            ".......",
            ".......",
            ".......",
    };
    static final String[] EYE = {
            ".......",
            "..###..",
            ".#...#.",
            "#..#..#",
            ".#...#.",
            "..###..",
            ".......",
    };
    static final String[] GEAR = {
            "..#.#..",
            ".#####.",
            "##...##",
            ".#...#.",
            "##...##",
            ".#####.",
            "..#.#..",
    };
    static final String[] SEARCH = {
            ".###...",
            "#...#..",
            "#...#..",
            "#...#..",
            ".###...",
            ".....#.",
            "......#",
    };
    static final String[] CLOSE = {
            "#.....#",
            ".#...#.",
            "..#.#..",
            "...#...",
            "..#.#..",
            ".#...#.",
            "#.....#",
    };
    static final String[] PERSON = {
            "..###..",
            "..###..",
            "..###..",
            ".......",
            ".#####.",
            "#######",
            "#######",
    };
    static final String[] LOGO = {
            "...#...",
            "..###..",
            "..###..",
            ".#####.",
            ".#####.",
            "#######",
            "#######",
    };
    static final String[] UP = {
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            "..###..",
            "..###..",
            "..###..",
    };
    static final String[] DOWN = {
            "..###..",
            "..###..",
            "..###..",
            "#######",
            ".#####.",
            "..###..",
            "...#...",
    };
    static final String[] PLUS = {
            "...#...",
            "...#...",
            "...#...",
            "#######",
            "...#...",
            "...#...",
            "...#...",
    };
    static final String[] MINUS = {
            ".......",
            ".......",
            ".......",
            "#######",
            ".......",
            ".......",
            ".......",
    };

    private Icons() {}

    static String[] of(Category category) {
        return switch (category) {
            case COMBAT -> SWORD;
            case MINING -> PICKAXE;
            case FARMING -> WHEAT;
            case MACROS -> PLAY;
            case MISC -> DOTS;
            case VISUALS -> EYE;
        };
    }

    /** Draws the icon with its top-left corner at x, y. Each run of filled pixels in a row is one rectangle. */
    static void draw(GuiGraphicsExtractor g, String[] icon, int x, int y, int color) {
        for (int row = 0; row < icon.length; row++) {
            String line = icon[row];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) != '#') {
                    col++;
                    continue;
                }
                int start = col;
                while (col < line.length() && line.charAt(col) == '#') {
                    col++;
                }
                g.fill(x + start, y + row, x + col, y + row + 1, color);
            }
        }
    }
}
