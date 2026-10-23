package com.slushybear.daedalus.ui;

import com.mojang.blaze3d.platform.Window;
import com.slushybear.daedalus.automation.MacroManager;
import com.slushybear.daedalus.config.DaedalusConfig;
import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.KeyNames;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.ModuleRegistry;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.ChoiceSetting;
import com.slushybear.daedalus.module.setting.KeybindSetting;
import com.slushybear.daedalus.module.setting.Setting;
import com.slushybear.daedalus.module.setting.SliderSetting;
import com.slushybear.daedalus.module.setting.TextSetting;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Daedalus menu: title bar, sidebar, and then either a category's modules plus their settings, the macro editor,
 * or the client settings page. The controls themselves live in {@link Ui}.
 *
 * The menu picks its own size instead of following Minecraft's GUI Scale (see {@link #menuScale}), so it is the same
 * comfortable size on every screen and the pixel font stays sharp.
 */
public final class DaedalusScreen extends Screen {
    private static final int MAX_W = 560;
    private static final int MAX_H = 330;
    private static final int TITLE_H = 22;
    private static final int FOOT_H = 14;
    private static final int CARD_H = 24;
    private static final int CARD_GAP = 3;
    private static final String VERSION = "v" + FabricLoader.getInstance().getModContainer("daedalus")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
    private static final List<String> SCALE_OPTIONS = List.of("Auto", "1x", "2x", "3x", "4x");

    // Remembered between openings, so the menu reopens where you left it.
    private static Category category = Category.COMBAT;
    private static Module selected;
    private static boolean settingsPage;

    private final Ui ui;
    private final MacroEditor macroEditor;
    private final Ui.Scroll listScroll = new Ui.Scroll();
    private final Ui.Scroll panelScroll = new Ui.Scroll();
    private final Ui.Scroll settingsScroll = new Ui.Scroll();
    private String query = "";

    /** GUI pixels per menu pixel, from the last frame. Mouse positions are divided by this. */
    private float scale = 1;
    private int wx, wy, ww, wh;
    private int sideW;
    private int panelW;

    public DaedalusScreen() {
        super(Component.literal("Daedalus"));
        ui = new Ui(Minecraft.getInstance());
        macroEditor = new MacroEditor(ui, this::onClose);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Saves whenever the menu closes, for any reason. A half-typed rename or value is kept first. */
    @Override
    public void removed() {
        ui.commitFocus();
        DaedalusConfig.save();
    }

    /**
     * How many screen pixels make one menu pixel. Auto picks the biggest whole number that leaves the menu
     * roughly two thirds of the screen wide, and never less than 2 on a normal sized window.
     * Whole numbers keep every font pixel the same size.
     */
    static int menuScale(int screenW, int screenH) {
        int s = Theme.scale();
        if (s == 0) {
            s = 1;
            while (screenW / (s + 1) >= 760 && screenH / (s + 1) >= 420) {
                s++;
            }
            if (s == 1 && screenW / 2 >= 400 && screenH / 2 >= 230) {
                s = 2;
            }
        }
        // A manual choice that would not fit is lowered until it does.
        while (s > 1 && (screenW / s < 400 || screenH / s < 230)) {
            s--;
        }
        return s;
    }

    // =====================================================================================================
    // Drawing
    // =====================================================================================================

    /** The dark layer over the game world. Replaces vanilla's blur. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, Theme.withAlpha(0x06040a, Theme.dim() / 100f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Window window = minecraft.getWindow();
        int pixels = menuScale(window.getWidth(), window.getHeight());
        scale = pixels / (float) window.getGuiScale();

        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        ui.beginFrame(graphics, (int) (mouseX / scale), (int) (mouseY / scale), pixels);

        int screenW = (int) (width / scale);
        int screenH = (int) (height / scale);
        ww = Math.min(screenW - 16, MAX_W);
        wh = Math.min(screenH - 16, MAX_H);
        wx = (screenW - ww) / 2;
        wy = (screenH - wh) / 2;
        boolean compact = ww < 500;
        sideW = compact ? 108 : 124;
        panelW = compact ? 132 : 156;

        boolean macros = !settingsPage && !searching() && category == Category.MACROS;
        drawFrame(!settingsPage && !macros);
        drawTitleBar();
        drawSidebar();
        if (settingsPage) {
            drawSettingsPage();
        } else if (macros) {
            macroEditor.draw(sidebarRight() + 1, mainTop(), wx + ww - 1, mainBottom());
        } else {
            drawModuleList();
            drawPanel();
        }
        drawFooter();
        ui.drawToast(wx + ww - 10, mainBottom() - 8, ww / 2);

        ui.endFrame();
        graphics.pose().popMatrix();
    }

    private int mainTop() {
        return wy + TITLE_H;
    }

    private int mainBottom() {
        return wy + wh - FOOT_H;
    }

    private int sidebarRight() {
        return wx + sideW;
    }

    private int panelLeft() {
        return wx + ww - 1 - panelW;
    }

    private void drawFrame(boolean withPanel) {
        int r = (Theme.radius() + 4) / 2;
        ui.round(wx, wy, ww, wh, r, Theme.line());
        // The whole inside starts as the sidebar colour, which also covers the title bar and footer.
        ui.round(wx + 1, wy + 1, ww - 2, wh - 2, Math.max(0, r - 1), Theme.side());
        int right = wx + ww - 1;
        ui.g.fill(sidebarRight() + 1, mainTop(), right, mainBottom(), Theme.bg());
        if (withPanel) {
            ui.g.fill(panelLeft() + 1, mainTop(), right, mainBottom(), Theme.panel());
            ui.g.fill(panelLeft(), mainTop(), panelLeft() + 1, mainBottom(), Theme.line());
        }
        ui.g.fill(wx + 1, mainTop() - 1, right, mainTop(), Theme.line());
        ui.g.fill(wx + 1, mainBottom(), right, mainBottom() + 1, Theme.line());
        ui.g.fill(sidebarRight(), mainTop(), sidebarRight() + 1, mainBottom(), Theme.line());
    }

    private void drawTitleBar() {
        int cy = wy + TITLE_H / 2;
        int x = wx + 9;
        ui.icon(Icons.LOGO, x, cy - 4, Theme.accent());
        x += Icons.SIZE + 5;
        Component logo = Component.literal("DAEDALUS").withStyle(ChatFormatting.BOLD);
        ui.g.text(font, logo, x, cy - 3, Theme.text(), false);
        x += font.width(logo) + 6;
        int versionW = font.width(VERSION) + 8;
        ui.box(x, cy - 6, versionW, 12, Theme.side(), Theme.line());
        ui.text(VERSION, x + 4, cy - 3, Theme.muted());
        int leftEdge = x + versionW + 8;

        // Right side, from the right edge inward.
        int rx = wx + ww - 7 - 16;
        ui.iconButton(rx, cy - 8, 16, Icons.CLOSE, this::onClose);
        rx -= 20;
        ui.iconButton(rx, cy - 8, 16, Icons.GEAR, () -> {
            settingsPage = !settingsPage;
            clearSearch();
        });
        rx = profileButton(rx - 4, cy - 8);
        int searchW = Math.min(130, rx - 4 - leftEdge);
        if (searchW >= 60) {
            ui.searchBox("search", rx - 4 - searchW, cy - 8, searchW, 16, query, this::setQuery);
        }
    }

    /** The profile chip, placed by its right edge. Click to cycle profiles. Returns its left edge. */
    private int profileButton(int right, int y) {
        String name = DaedalusConfig.activeProfile();
        int w = font.width(name) + Icons.SIZE + 16;
        int x = right - w;
        boolean hovered = ui.hover(x, y, w, 16);
        ui.box(x, y, w, 16, Theme.card(), hovered ? Theme.accent() : Theme.line());
        int color = hovered ? Theme.text() : Theme.muted();
        ui.icon(Icons.PERSON, x + 6, y + 4, color);
        ui.text(name, x + 6 + Icons.SIZE + 4, Ui.textY(y, 16), color);
        ui.hit(x, y, w, 16, Ui.left(() -> loadProfile(DaedalusConfig.nextProfile())));
        return x;
    }

    private void setQuery(String text) {
        query = text;
        listScroll.reset();
        if (!text.isBlank()) {
            settingsPage = false;
        }
    }

    private boolean searching() {
        return !query.isBlank();
    }

    private void clearSearch() {
        query = "";
        if (ui.isFocused("search")) {
            ui.commitFocus();
        }
    }

    private void drawSidebar() {
        int x = wx + 5;
        int w = sideW - 9;
        int y = mainTop() + 5;
        boolean showingCategory = !settingsPage && !searching();
        for (Category c : Category.values()) {
            String count;
            if (c == Category.MACROS) {
                count = String.valueOf(MacroManager.all().size());
            } else {
                List<Module> modules = ModuleRegistry.in(c);
                count = modules.isEmpty() ? null : ModuleRegistry.enabledCount(modules) + "/" + modules.size();
            }
            navItem(c.name(), x, y, w, Icons.of(c), c.label(), count, showingCategory && category == c, () -> openCategory(c));
            y += 16;
        }
        navItem("settings", x, mainBottom() - 19, w, Icons.GEAR, "Settings", null, settingsPage, () -> {
            settingsPage = true;
            clearSearch();
        });
    }

    private void openCategory(Category c) {
        if (category != c) {
            listScroll.reset();
        }
        category = c;
        settingsPage = false;
        clearSearch();
    }

    private void navItem(String id, int x, int y, int w, String[] icon, String label, String count, boolean active,
                         Runnable onClick) {
        int h = 14;
        float hover = ui.hoverFade("nav:" + id, x, y, w, h);
        if (active) {
            ui.round(x, y, w, h, Theme.corner(h), Theme.card());
        } else if (hover > 0) {
            ui.round(x, y, w, h, Theme.corner(h), Theme.mix(Theme.accent(), Theme.side(), 0.08 * hover));
        }
        int color = active ? Theme.text() : Theme.mix(Theme.text(), Theme.muted(), hover);
        ui.icon(icon, x + 4, y + (h - Icons.SIZE) / 2, active ? Theme.accent() : color);
        int countW = count == null ? 0 : font.width(count) + 4;
        ui.text(ui.truncate(label, w - 16 - countW - 2), x + 15, Ui.textY(y, h), color);
        if (count != null) {
            ui.textRight(count, x + w - 4, Ui.textY(y, h), Theme.muted());
        }
        ui.hit(x, y, w, h, Ui.left(onClick));
    }

    // ---- Module list (middle column) ----

    private void drawModuleList() {
        int left = sidebarRight() + 1;
        int right = panelLeft();
        int x = left + 8;
        int w = right - left - 16;
        int y = mainTop() + 7;

        List<Module> modules;
        String eyebrow;
        String heading;
        String meta;
        if (searching()) {
            modules = ModuleRegistry.search(query);
            eyebrow = "SEARCH";
            heading = modules.size() + (modules.size() == 1 ? " result" : " results");
            meta = "\"" + query.trim() + "\"";
        } else {
            modules = ModuleRegistry.in(category);
            eyebrow = "CATEGORY";
            heading = category.label();
            meta = ModuleRegistry.enabledCount(modules) + " of " + modules.size() + " enabled";
        }
        ui.text(eyebrow, x, y, Theme.accent());
        ui.heading(heading, x, y + 11, Theme.text());
        ui.textRight(ui.truncate(meta, w / 2), x + w, y + 3, Theme.muted());

        int top = y + 30;
        int cy = ui.beginScroll(left, top, right, mainBottom() - 4, listScroll);
        if (modules.isEmpty()) {
            String empty = searching() ? "No modules match that search." : "Nothing here yet.";
            cy = ui.wrapped(empty, x, cy + 10, w, Theme.muted());
        }
        for (Module module : modules) {
            moduleCard(module, x, cy, w);
            cy += CARD_H + CARD_GAP;
        }
        ui.endScroll(listScroll, cy);
    }

    private void moduleCard(Module module, int x, int y, int w) {
        boolean isSelected = module == selected;
        float hover = ui.hoverFade("card:" + module.name(), x, y, w, CARD_H);
        int border = isSelected ? Theme.accent()
                : Theme.mix(Theme.mix(Theme.accent(), Theme.line(), 0.55), Theme.line(), hover);
        ui.box(x, y, w, CARD_H, isSelected ? Theme.cardSelected() : Theme.card(), border);

        // Left click selects, right click toggles. Added before the switch so the switch's own hit wins.
        ui.hit(x, y, w, CARD_H, (button, mx, my) -> {
            if (button == 0) {
                select(module);
            } else if (button == 1) {
                setEnabled(module, !module.enabled());
            }
        });

        int toggleX = x + w - 7 - Ui.TOGGLE_W;
        ui.toggle("card:" + module.name(), toggleX, y + (CARD_H - Ui.TOGGLE_H) / 2, module.enabled(),
                () -> setEnabled(module, !module.enabled()));

        int textRight = toggleX - 6;
        if (!KeyNames.isNone(module.keybind())) {
            String key = KeyNames.display(module.keybind());
            int keyW = Math.min(w / 3, font.width(key) + 8);
            textRight -= keyW;
            ui.box(textRight, y + 6, keyW, 12, Theme.card(), Theme.line());
            ui.text(ui.truncate(key, keyW - 6), textRight + 4, y + 8, Theme.muted());
            textRight -= 6;
        }

        int textX = x + 7;
        int nameColor = module.enabled() ? Theme.text() : Theme.mix(Theme.text(), Theme.card(), 0.72);
        String name = ui.truncate(module.name(), textRight - textX);
        ui.text(name, textX, y + 4, nameColor);
        if (searching()) {
            // In search results, show which category each module came from.
            String tag = module.category().label();
            int tagX = textX + font.width(name) + 5;
            int tagW = font.width(tag) + 6;
            if (tagX + tagW <= textRight) {
                ui.box(tagX, y + 3, tagW, 11, Theme.card(), Theme.line());
                ui.text(tag, tagX + 3, y + 5, Theme.accent());
            }
        }
        ui.text(ui.truncate(module.description(), textRight - textX), textX, y + 14, Theme.muted());
    }

    private void select(Module module) {
        if (selected != module) {
            selected = module;
            panelScroll.reset();
        }
    }

    // ---- Settings panel (right column) ----

    private void drawPanel() {
        int left = panelLeft() + 1;
        int right = wx + ww - 1;
        int x = left + 8;
        int w = right - left - 16;
        Module module = selected;
        boolean visible = module != null && (searching() || module.category() == category);
        if (!visible) {
            ui.wrapped("Select a module to edit its settings.", x, mainTop() + 30, w, Theme.muted());
            return;
        }

        int y = ui.beginScroll(left, mainTop(), right, mainBottom(), panelScroll) + 9;
        ui.text(module.category().label().toUpperCase(), x, y, Theme.accent());
        int bx = x + w - 14;
        ui.iconButton(bx, y - 3, 14, Icons.CLOSE, () -> selected = null);
        int resetW = font.width("Reset") + 10;
        bx -= resetW + 3;
        ui.chip(bx, y - 3, resetW, 14, "Reset", () -> {
            module.resetSettings();
            ui.toast(module.name() + " reset", true);
        });
        y += 15;
        ui.text(ui.truncate(module.name(), w), x, y, Theme.text());
        y += 13;
        y = ui.wrapped(module.description() + ".", x, y, w, Theme.muted()) + 5;

        String id = module.name();
        y = ui.toggleRow("panel-on:" + id, "Enabled", x, y, w, module.enabled(), () -> setEnabled(module, !module.enabled()));
        y = ui.bindRow("panel-key:" + id, "Keybind", x, y, w, module.keybind(), module::setKeybind);
        ui.line(x, y, x + w);
        y += 7;
        for (Setting<?> setting : module.settings()) {
            y = drawSetting(id + ":" + setting.name(), setting, x, y, w);
        }
        ui.endScroll(panelScroll, y + 4);
    }

    /** Draws one setting at y and returns the y where the next one starts. */
    private int drawSetting(String id, Setting<?> setting, int x, int y, int w) {
        switch (setting) {
            case BoolSetting s -> {
                return ui.toggleRow("set:" + id, s.name(), x, y, w, s.get(), s::toggle);
            }
            case SliderSetting s -> {
                return ui.sliderRow(s.name(), s.format(), x, y, w, s.fraction(), s::setFraction);
            }
            case ChoiceSetting s -> {
                ui.text(ui.truncate(s.name(), w), x, y, Theme.text());
                ui.segmented(x, y + 11, w, 14, s.options(), s.get(), s::set);
                return y + 31;
            }
            case KeybindSetting s -> {
                return ui.bindRow("set:" + id, s.name(), x, y, w, s.get(), s::set);
            }
            case TextSetting s -> {
                ui.text(ui.truncate(s.name(), w), x, y, Theme.muted());
                ui.textField("set:" + id, x, y + 11, w, 14, s.get(), "", TextSetting.MAX_LENGTH, Ui.ANY_TEXT, s::set);
                return y + 31;
            }
            default -> {
                return y;
            }
        }
    }

    // ---- Client settings page ----

    private void drawSettingsPage() {
        int left = sidebarRight() + 1;
        int right = wx + ww - 1;
        int x = left + 8;
        int w = right - left - 16;
        int y = mainTop() + 7;

        ui.text("CLIENT", x, y, Theme.accent());
        ui.heading("Settings", x, y + 11, Theme.text());
        ui.chipRight(x + w, y + 4, "Reset all", () -> {
            Theme.reset();
            MenuKeys.resetKey();
            ui.toast("Settings reset", true);
        });

        int sy = ui.beginScroll(left, y + 30, right, mainBottom() - 4, settingsScroll);

        // Theme presets, full width.
        int presetsH = 66;
        section(x, sy, w, presetsH, "THEME");
        int tileW = (w - 16 - 5 * 5) / 6;
        for (int i = 0; i < Theme.PRESETS.size(); i++) {
            presetTile(Theme.PRESETS.get(i), x + 8 + i * (tileW + 5), sy + 22, tileW, 36);
        }
        sy += presetsH + 6;

        // Colours and Interface, side by side.
        int colW = (w - 6) / 2;
        int colH = 122;
        int inner = colW - 16;
        section(x, sy, colW, colH, "COLORS");
        int iy = sy + 22;
        iy = colorRow("Primary color", "hex-accent", x + 8, iy, inner, Theme.accentRgb(), Theme::setAccent);
        iy = colorRow("Text color", "hex-text", x + 8, iy, inner, Theme.textRgb(), Theme::setText);
        iy = ui.sliderRow("Menu tint", Theme.tint() + "%", x + 8, iy, inner, Theme.tint() / (double) Theme.MAX_TINT,
                f -> Theme.setTint((int) Math.round(f * Theme.MAX_TINT)));
        ui.sliderRow("Corner radius", Theme.radius() + "px", x + 8, iy, inner, Theme.radius() / (double) Theme.MAX_RADIUS,
                f -> Theme.setRadius((int) Math.round(f * Theme.MAX_RADIUS)));

        int cx = x + colW + 6;
        section(cx, sy, colW, colH, "INTERFACE");
        iy = sy + 22;
        iy = ui.toggleRow("animations", "Animations", cx + 8, iy, inner, Theme.animations(),
                () -> Theme.setAnimations(!Theme.animations()));
        iy = ui.sliderRow("World dim", Theme.dim() + "%", cx + 8, iy, inner, Theme.dim() / (double) Theme.MAX_DIM,
                f -> Theme.setDim((int) Math.round(f * Theme.MAX_DIM)));
        ui.text("Menu size", cx + 8, iy, Theme.text());
        ui.segmented(cx + 8, iy + 11, inner, 14, SCALE_OPTIONS, SCALE_OPTIONS.get(Theme.scale()),
                option -> Theme.setScale(SCALE_OPTIONS.indexOf(option)));
        iy += 31;
        ui.bindRow("open-key", "Open menu", cx + 8, iy, inner, MenuKeys.keyName(), MenuKeys::setKey);
        sy += colH + 6;

        // Profiles, full width.
        int profilesH = 44;
        section(x, sy, w, profilesH, "PROFILES");
        ui.segmented(x + 8, sy + 22, w - 16, 14, DaedalusConfig.PROFILES, DaedalusConfig.activeProfile(), this::loadProfile);
        sy += profilesH;
        ui.endScroll(settingsScroll, sy + 2);
    }

    private void section(int x, int y, int w, int h, String eyebrow) {
        ui.box(x, y, w, h, Theme.panel(), Theme.line());
        ui.text(eyebrow, x + 8, y + 8, Theme.accent());
    }

    private void presetTile(Theme.Preset preset, int x, int y, int w, int h) {
        boolean on = Theme.accentRgb() == preset.color();
        float hover = ui.hoverFade("preset:" + preset.name(), x, y, w, h);
        int border = on ? Theme.accent() : Theme.mix(Theme.mix(Theme.accent(), Theme.line(), 0.55), Theme.line(), hover);
        ui.box(x, y, w, h, Theme.card(), border);
        // A small preview: the preset's colour over a dark background, with two bars.
        int sx = x + 3;
        int sy = y + 3;
        int sw = w - 6;
        ui.round(sx, sy, sw, 18, Theme.corner(18), Theme.mix(preset.color(), 0x141019, 0.18));
        int barW = Math.round(sw * 0.55f);
        ui.round(sx + 3, sy + 12, barW - 3, 3, 1, 0xFF000000 | preset.color());
        ui.round(sx + barW + 2, sy + 12, Math.round(sw * 0.25f), 3, 1, Theme.mix(preset.color(), 0x141019, 0.45));
        ui.text(ui.truncate(preset.name(), w - 6), x + 3, y + 25, Theme.text());
        ui.hit(x, y, w, h, Ui.left(() -> Theme.setAccent(preset.color())));
    }

    /**
     * Label, a colour swatch and a hex field. Clicking the field selects all of it, so you can type a new colour
     * straight away; it applies as soon as 6 valid hex digits are in. Returns the y for the next row.
     */
    private int colorRow(String label, String id, int x, int y, int w, int rgb, Consumer<Integer> onColor) {
        int fieldW = 54;
        int fieldX = x + w - fieldW;
        ui.text(ui.truncate(label, w - fieldW - 22), x, y + 3, Theme.text());
        ui.textField(id, fieldX, y, fieldW, 14, Theme.hex(rgb), "#rrggbb", 7, Ui.HEX, text -> {
            int parsed = Theme.parseHex(text);
            if (parsed >= 0) {
                onColor.accept(parsed);
            }
        }, null, true);
        ui.box(fieldX - 18, y, 14, 14, 0xFF000000 | rgb, Theme.line());
        return y + 18;
    }

    // ---- Footer ----

    private void drawFooter() {
        int y = mainBottom() + 4;
        int x = wx + 10;
        String count = String.valueOf(ModuleRegistry.enabledCount(ModuleRegistry.all()));
        ui.text(count, x, y, Theme.accent());
        x += font.width(count);
        ui.text(" modules enabled", x, y, Theme.muted());
        x += font.width(" modules enabled") + 14;
        String profile = "Profile: " + DaedalusConfig.activeProfile();
        ui.text(profile, x, y, Theme.muted());
        x += font.width(profile) + 14;
        String hint = "Right click a module to toggle";
        if (wx + ww - 10 - font.width(hint) > x) {
            ui.textRight(hint, wx + ww - 10, y, Theme.muted());
        }
    }

    private void setEnabled(Module module, boolean value) {
        module.setEnabled(value);
        ui.toast(module.name() + (value ? " enabled" : " disabled"), value);
    }

    private void loadProfile(String name) {
        if (!name.equals(DaedalusConfig.activeProfile())) {
            DaedalusConfig.switchProfile(name);
            ui.toast("Loaded profile " + name, true);
        }
    }

    // =====================================================================================================
    // Input. Mouse positions arrive in GUI pixels and are turned into menu pixels.
    // =====================================================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ui.mouseClicked(event.x() / scale, event.y() / scale, event.button());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return ui.mouseDragged(event.x() / scale);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ui.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ui.mouseScrolled(mouseX / scale, mouseY / scale, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ui.keyPressed(event)) {
            return true;
        }
        if (MenuKeys.OPEN_MENU.matches(event)) {
            onClose();
            return true;
        }
        // Handles Esc to close.
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (ui.charTyped(event)) {
            return true;
        }
        // "/" jumps to the search box.
        if (event.codepoint() == '/') {
            ui.focus("search", query, 32, Ui.ANY_TEXT, this::setQuery, null, false);
            return true;
        }
        return false;
    }
}
