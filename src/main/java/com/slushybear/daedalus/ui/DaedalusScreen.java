package com.slushybear.daedalus.ui;

import com.mojang.blaze3d.platform.Window;
import com.slushybear.daedalus.automation.Macro;
import com.slushybear.daedalus.automation.MacroManager;
import com.slushybear.daedalus.config.DaedalusConfig;
import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.ModuleRegistry;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.ChoiceSetting;
import com.slushybear.daedalus.module.setting.KeybindSetting;
import com.slushybear.daedalus.module.setting.MultiChoiceSetting;
import com.slushybear.daedalus.module.setting.Setting;
import com.slushybear.daedalus.module.setting.SliderSetting;
import com.slushybear.daedalus.module.setting.TextSetting;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Daedalus menu. Two panels in a rounded window:
 *
 * - The sidebar: logo, search, the categories as an accordion (the open one's features hang off a thin "labyrinth"
 *   path line), and a footer with the profile switcher and the theme and close buttons.
 * - The content panel: the selected feature's settings, the macro editor, or the client settings page.
 *
 * What is open and selected lives in {@link MenuState}; the controls are drawn by {@link Ui}; every colour and size
 * comes from {@link Theme}. The menu picks its own size instead of following Minecraft's GUI Scale (see
 * {@link #menuScale}), so it is the same comfortable size on every screen and the pixel font stays sharp.
 */
public final class DaedalusScreen extends Screen {
    private static final List<String> SCALE_OPTIONS = List.of("Auto", "1x", "2x", "3x", "4x");

    /** Kept between openings, so the menu reopens where you left it. */
    private static final MenuState STATE = new MenuState(ModuleRegistry::all);

    private final Ui ui;
    private final MacroEditor macroEditor;
    private final Ui.Scroll sideScroll = new Ui.Scroll();
    private final Ui.Scroll contentScroll = new Ui.Scroll();
    /** What the content panel showed last frame. When it changes, the content scrolls back to the top. */
    private Object shown;

    /** GUI pixels per menu pixel, from the last frame. Mouse positions are divided by this. */
    private float scale = 1;
    private int wx, wy, ww, wh;
    private int sideRight;

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
        graphics.fill(0, 0, width, height, Theme.withAlpha(0x050505, Theme.dim() / 100f));
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
        ww = Math.min(screenW - 16, Theme.MAX_WINDOW_W);
        wh = Math.min(screenH - 16, Theme.MAX_WINDOW_H);
        wx = (screenW - ww) / 2;
        wy = (screenH - wh) / 2;
        sideRight = wx + Math.max(118, Math.round(ww * Theme.SIDEBAR_FRACTION));

        drawFrame();
        drawSidebar();
        drawContent();
        ui.drawToast(wx + ww - 10, wy + wh - 8, ww / 2);
        ui.drawPopup(wy + wh - 4);

        ui.endFrame();
        graphics.pose().popMatrix();
    }

    /** The window: a 1 pixel border, the sidebar on the left and the darker content panel on the right. */
    private void drawFrame() {
        int r = Theme.windowCorner();
        ui.round(wx, wy, ww, wh, r, Theme.line());
        ui.round(wx + 1, wy + 1, ww - 2, wh - 2, Math.max(0, r - 1), Theme.side());
        int contentX = sideRight + 1;
        ui.round(contentX, wy + 1, wx + ww - 1 - contentX, wh - 2, Math.max(0, r - 1), Theme.bg());
        // Square off the content panel's left corners, where it meets the sidebar.
        ui.g.fill(contentX, wy + 1, contentX + r, wy + wh - 1, Theme.bg());
        ui.g.fill(sideRight, wy + 1, sideRight + 1, wy + wh - 1, Theme.line());
    }

    // =====================================================================================================
    // Sidebar
    // =====================================================================================================

    private void drawSidebar() {
        int left = wx + 1;
        int right = sideRight;

        // Header: logo and the serif wordmark.
        ui.logo(left + 8, wy + 7, Theme.LOGO_SIZE);
        ui.wordmark("Daedalus", left + 8 + Theme.LOGO_SIZE + 5, wy + 11, Theme.text());

        // Search.
        int searchY = wy + Theme.SIDEBAR_HEADER_H;
        ui.searchBox("search", left + 6, searchY, right - left - 12, Theme.SEARCH_H, STATE.query(), "Find a feature",
                this::setQuery);

        // The category list scrolls between the search box and the footer.
        int footerTop = wy + wh - Theme.SIDEBAR_FOOTER_H;
        int listTop = searchY + Theme.SEARCH_H + 5;
        int y = ui.beginScroll(left, listTop, right, footerTop, sideScroll) + 2;
        if (STATE.noResults()) {
            int cx = left;
            int cw = right - left;
            ui.textCentered("No features match", cx, cw, y + 18, Theme.text());
            ui.textCentered("Try a different name.", cx, cw, y + 30, Theme.secondary());
            y += 44;
        }
        for (Category category : STATE.visibleCategories()) {
            y = categoryHeader(category, left, y, right);
            y = branch(category, left, y);
        }
        if (!STATE.searching()) {
            boolean on = STATE.page() == MenuState.Page.SETTINGS;
            y = headerRow("settings", Icons.GEAR, "Settings", null, on, left, y, right, STATE::openSettings);
        }
        ui.endScroll(sideScroll, y + 4);

        drawFooter(left, footerTop, right);
    }

    /** A category row: icon, name and an on/total count. Clicking it opens or closes the category. */
    private int categoryHeader(Category category, int left, int y, int right) {
        boolean open = STATE.isExpanded(category);
        String count;
        if (category == Category.MACROS) {
            List<Macro> macros = MacroManager.all();
            int on = (int) macros.stream().filter(m -> !m.paused()).count();
            count = (on > 0 ? on + "/" : "") + macros.size();
        } else {
            int on = STATE.enabledCount(category);
            count = (on > 0 ? on + "/" : "") + STATE.totalCount(category);
        }
        Runnable onClick = () -> {
            if (!STATE.searching()) {
                STATE.toggleCategory(category);
            }
        };
        return headerRow(category.name(), Icons.of(category), category.label(), count, open, left, y, right, onClick);
    }

    private int headerRow(String id, String[] icon, String label, String count, boolean open, int left, int y, int right,
                          Runnable onClick) {
        int h = Theme.CATEGORY_H;
        float hover = ui.hoverFade("cat:" + id, left, y, right - left, h);
        int color = open ? Theme.text() : Theme.mix(Theme.text(), Theme.secondary(), hover);
        ui.icon(icon, left + 8, y + (h - Icons.SIZE) / 2, open ? Theme.accent() : color);
        int countW = 0;
        if (count != null) {
            // The "on" part of the count is gold; the total is muted.
            int slash = count.indexOf('/');
            String total = slash < 0 ? count : count.substring(slash);
            int tx = right - 8 - ui.width(total);
            ui.text(total, tx, Ui.textY(y, h), Theme.muted());
            if (slash >= 0) {
                String on = count.substring(0, slash);
                ui.text(on, tx - ui.width(on), Ui.textY(y, h), Theme.accent());
            }
            countW = ui.width(count) + 6;
        }
        ui.text(ui.truncate(label, right - left - 28 - countW), left + 20, Ui.textY(y, h), color);
        ui.hit(left, y, right - left, h, Ui.left(onClick));
        return y + h;
    }

    /**
     * The open category's features, hanging off a vertical path line, each with a short tick. The selected one gets a
     * gold diamond on the line, a gold tick and light gold text. Opening and closing slides over about 120 ms.
     * Returns the y below the branch.
     */
    private int branch(Category category, int left, int y) {
        boolean macros = category == Category.MACROS;
        List<Macro> macroList = macros ? MacroManager.all() : List.of();
        List<Module> modules = macros ? List.of() : STATE.modulesUnder(category);
        int items = macros ? macroList.size() + 1 : Math.max(1, modules.size());
        int fullH = items * Theme.BRANCH_ITEM_H + 4;
        float t = ui.anim.to("branch:" + category.name(), STATE.isExpanded(category) ? 1 : 0, Theme.ACCORDION_MS);
        int shownH = Math.round(fullH * t);
        if (shownH <= 0) {
            return y;
        }
        int lineX = left + Theme.BRANCH_INDENT;
        ui.g.fill(lineX, y, lineX + 1, y + shownH - 2, Theme.pathLine());
        int iy = y + 2;
        int limit = y + shownH;
        if (macros) {
            String selectedName = STATE.page() == MenuState.Page.MACROS ? MacroEditor.selectedName() : null;
            for (Macro macro : macroList) {
                if (iy + Theme.BRANCH_ITEM_H > limit) {
                    break;
                }
                branchItem("macro:" + macro.name(), macro.name(), macro.name().equals(selectedName), !macro.paused(),
                        lineX, iy, () -> {
                            macroEditor.select(macro);
                            STATE.openMacros();
                        });
                iy += Theme.BRANCH_ITEM_H;
            }
            if (iy + Theme.BRANCH_ITEM_H <= limit) {
                branchItem("macro:new", "+ New macro", false, false, lineX, iy, () -> {
                    macroEditor.newMacro();
                    STATE.openMacros();
                });
            }
        } else if (modules.isEmpty()) {
            if (iy + Theme.BRANCH_ITEM_H <= limit) {
                ui.g.fill(lineX + 1, iy + 6, lineX + 1 + Theme.PATH_TICK, iy + 7, Theme.pathLine());
                ui.text("Nothing here yet", lineX + Theme.PATH_TICK + 5, Ui.textY(iy, Theme.BRANCH_ITEM_H), Theme.muted());
            }
        } else {
            boolean showingModule = STATE.page() == MenuState.Page.MODULE;
            for (Module module : modules) {
                if (iy + Theme.BRANCH_ITEM_H > limit) {
                    break;
                }
                branchItem("module:" + module.name(), module.name(), showingModule && module == STATE.selected(),
                        module.enabled(), lineX, iy, () -> STATE.selectModule(module));
                iy += Theme.BRANCH_ITEM_H;
            }
        }
        return y + shownH;
    }

    private void branchItem(String id, String name, boolean selected, boolean enabled, int lineX, int y, Runnable onClick) {
        int h = Theme.BRANCH_ITEM_H;
        int textX = lineX + Theme.PATH_TICK + 5;
        int w = sideRight - 6 - lineX;
        float hover = ui.hoverFade(id, lineX, y, w, h);
        int mid = y + h / 2;
        ui.g.fill(lineX + 1, mid, lineX + 1 + Theme.PATH_TICK, mid + 1, selected ? Theme.accent() : Theme.pathLine());
        if (selected) {
            ui.diamond(lineX + 0.5f, mid + 0.5f, Theme.PATH_MARKER + 1, Theme.accent());
        }
        int color = selected ? Theme.accentLight() : Theme.mix(Theme.text(), Theme.secondary(), hover);
        String label = ui.truncate(name, sideRight - 8 - textX - (enabled ? 6 : 0));
        ui.text(label, textX, Ui.textY(y, h), color);
        if (enabled) {
            int dotX = textX + ui.width(label) + 3;
            ui.round(dotX, mid - 1, Theme.ENABLED_DOT, Theme.ENABLED_DOT, 1, Theme.accent());
        }
        ui.hit(lineX - 4, y, w + 4, h, Ui.left(onClick));
    }

    /** Profile switcher on the left; theme and close buttons on the right. */
    private void drawFooter(int left, int top, int right) {
        ui.g.fill(left, top, right, top + 1, Theme.line());
        ui.spaced("PROFILE", left + 8, top + 5, Theme.muted());
        int buttonW = Math.min(76, right - left - 50);
        ui.dropdown("profile", left + 8, top + 14, buttonW, 12, DaedalusConfig.PROFILES, DaedalusConfig.activeProfile(),
                this::loadProfile);
        int iconY = top + (Theme.SIDEBAR_FOOTER_H - 11) / 2 + 1;
        ui.plainIcon(right - 6 - 11, iconY, 11, Icons.CLOSE, this::onClose);
        ui.plainIcon(right - 6 - 11 - 13, iconY, 11, Icons.PALETTE, STATE::openSettings);
    }

    private void setQuery(String text) {
        STATE.setQuery(text);
        sideScroll.reset();
    }

    // =====================================================================================================
    // Content panel
    // =====================================================================================================

    private void drawContent() {
        int left = sideRight + 1;
        int right = wx + ww - 1;
        int top = wy + 1;
        int bottom = wy + wh - 1;

        Object now = switch (STATE.page()) {
            case MODULE -> STATE.selected();
            case MACROS -> "macros";
            case SETTINGS -> "settings";
        };
        if (now != shown) {
            shown = now;
            contentScroll.reset();
        }

        switch (STATE.page()) {
            case MACROS -> macroEditor.draw(left, top, right, bottom);
            case SETTINGS -> drawSettingsPage(left, top, right, bottom);
            case MODULE -> drawModulePage(left, top, right, bottom);
        }
    }

    private void drawModulePage(int left, int top, int right, int bottom) {
        Module module = STATE.selected();
        int x = left + Theme.CONTENT_PAD_X;
        int w = right - left - 2 * Theme.CONTENT_PAD_X;
        if (module == null) {
            ui.text("Pick a feature on the left.", x, top + 30, Theme.secondary());
            return;
        }
        int y = ui.beginScroll(left, top, right, bottom, contentScroll) + Theme.CONTENT_PAD_Y;
        ui.breadcrumb(module.category().label(), module.name(), x, y);
        y += 14;

        // Header controls, right to left: the on/off switch, the keybind, and reset.
        int switchX = x + w - Theme.SWITCH_W;
        ui.toggle("module:" + module.name(), switchX, y + 4, module.enabled(), () -> setEnabled(module, !module.enabled()));
        int bindW = ui.bindButton("key:" + module.name(), switchX - 8, y + 2, module.keybind(), module::setKeybind);
        int resetX = switchX - 8 - bindW - 4 - 11;
        ui.plainIcon(resetX, y + 3, 11, Icons.RESET, () -> {
            STATE.resetSelected();
            ui.toast(module.name() + " reset", true);
        });

        ui.heading(ui.truncateHeading(module.name(), resetX - x - 6), x, y, Theme.text());
        y += 18;
        y = ui.wrapped(module.description() + ".", x, y, w, Theme.secondary()) + 1;

        Map<String, List<Setting<?>>> groups = module.groups();
        if (groups.isEmpty()) {
            y = ui.section("General", x, y, w);
            ui.text("This feature has no settings. Use the switch above to turn it on.", x, y + 4, Theme.secondary());
            y += 16;
        }
        for (Map.Entry<String, List<Setting<?>>> group : groups.entrySet()) {
            y = ui.section(group.getKey(), x, y, w);
            List<Setting<?>> settings = group.getValue();
            for (int i = 0; i < settings.size(); i++) {
                y = settingRow(module.name(), settings.get(i), x, y, w, i == settings.size() - 1);
            }
        }
        ui.endScroll(contentScroll, y + Theme.CONTENT_PAD_Y);
    }

    /** Draws one setting as a row: name and description on the left, its control on the right. Returns the next y. */
    private int settingRow(String moduleName, Setting<?> setting, int x, int y, int w, boolean last) {
        String id = moduleName + ":" + setting.name();
        String label = setting.name();
        String description = setting.description();
        int column = Math.round(w * Theme.CONTROL_COLUMN);
        switch (setting) {
            case BoolSetting s -> {
                return row(label, description, x, y, w, Theme.CHECK_SIZE, Theme.CHECK_SIZE, last,
                        (cx, cy) -> ui.checkbox(id, cx, cy, s.get(), s::toggle));
            }
            case SliderSetting s -> {
                return row(label, description, x, y, w, column, 8, last,
                        (cx, cy) -> ui.sliderWithValue(cx, cy, column, s.fraction(), s.display(), s::setFraction));
            }
            case ChoiceSetting s -> {
                if (s.options().size() > Theme.MAX_SEGMENTS) {
                    int dw = Math.min(column, 90);
                    return row(label, description, x, y, w, dw, Ui.ROW_H, last,
                            (cx, cy) -> ui.dropdown(id, cx, cy, dw, Ui.ROW_H, s.options(), s.get(), s::set));
                }
                int sw = Math.min(ui.segmentedWidth(s.options()), Math.round(w * 0.6f));
                return row(label, description, x, y, w, sw, Ui.ROW_H, last,
                        (cx, cy) -> ui.segmented(cx, cy, sw, Ui.ROW_H, s.options(), s.get(), s::set));
            }
            case KeybindSetting s -> {
                int bw = Math.max(36, ui.width("Press a key...") + Icons.SIZE + 14);
                return row(label, description, x, y, w, bw, Ui.ROW_H, last,
                        (cx, cy) -> ui.bindButton(id, cx + bw, cy, s.get(), s::set));
            }
            case TextSetting s -> {
                String error = s.draftError();
                int h = Ui.ROW_H + (error == null ? 0 : 11);
                return row(label, description, x, y, w, column, h, last, (cx, cy) -> {
                    ui.textFieldError(id, cx, cy, column, Ui.ROW_H, s.shown(), TextSetting.MAX_LENGTH + 16, s::edit,
                            error != null);
                    if (error != null) {
                        ui.text(ui.truncate(error, column), cx, cy + Ui.ROW_H + 3, Theme.danger());
                    }
                });
            }
            case MultiChoiceSetting s -> {
                // Chips need the full width, so the name goes above them.
                int top = y + Theme.ROW_PAD_Y;
                ui.text(label, x, top, Theme.text());
                if (!description.isEmpty()) {
                    ui.text(ui.truncate(description, w), x, top + 10, Theme.secondary());
                    top += 10;
                }
                int end = ui.chipGrid(x, top + 13, w, s.options(), s::has, s::toggle) + Theme.ROW_PAD_Y + 1;
                if (!last) {
                    ui.g.fill(x, end - 1, x + w, end, Theme.rowDivider());
                }
                return end;
            }
            default -> {
                return y;
            }
        }
    }

    /** Draws a control at its top-left corner. */
    private interface Control {
        void draw(int x, int y);
    }

    /**
     * One row of the content panel: label and optional description on the left, a control of the given size on the
     * right, both centered vertically, and a faint line under it unless it is the last row in its section.
     */
    private int row(String label, String description, int x, int y, int w, int controlW, int controlH, boolean last,
                    Control control) {
        boolean hasDescription = description != null && !description.isEmpty();
        int textH = hasDescription ? 18 : 8;
        int rowH = Math.max(textH, controlH) + 2 * Theme.ROW_PAD_Y;
        int textW = w - controlW - 10;
        int ty = y + (rowH - textH) / 2;
        ui.text(ui.truncate(label, textW), x, ty, Theme.text());
        if (hasDescription) {
            ui.text(ui.truncate(description, textW), x, ty + 10, Theme.secondary());
        }
        control.draw(x + w - controlW, y + (rowH - controlH) / 2);
        if (!last) {
            ui.g.fill(x, y + rowH - 1, x + w, y + rowH, Theme.rowDivider());
        }
        return y + rowH;
    }

    // ---- Client settings page ----

    private void drawSettingsPage(int left, int top, int right, int bottom) {
        int x = left + Theme.CONTENT_PAD_X;
        int w = right - left - 2 * Theme.CONTENT_PAD_X;
        int y = ui.beginScroll(left, top, right, bottom, contentScroll) + Theme.CONTENT_PAD_Y;
        ui.breadcrumb("Client", "Settings", x, y);
        y += 14;
        int resetW = ui.chipRight(x + w, y + 3, "Reset all", () -> {
            Theme.reset();
            MenuKeys.resetKey();
            ui.toast("Settings reset", true);
        });
        ui.heading(ui.truncateHeading("Settings", w - resetW - 6), x, y, Theme.text());
        y += 18;
        y = ui.wrapped("How the menu looks and behaves. Shared by every profile.", x, y, w, Theme.secondary()) + 1;

        y = ui.section("Theme", x, y, w);
        y = presets(x, y + 4, w) + 4;
        int column = Math.round(w * Theme.CONTROL_COLUMN);
        y = row("Accent colour", "Used for anything on, selected or focused", x, y, w, 80, Ui.ROW_H, false,
                (cx, cy) -> colorField("hex-accent", cx, cy, Theme.accentRgb(), Theme::setAccent));
        y = row("Text colour", "Labels; descriptions are a dimmer shade of it", x, y, w, 80, Ui.ROW_H, false,
                (cx, cy) -> colorField("hex-text", cx, cy, Theme.textRgb(), Theme::setText));
        y = row("Tint", "Mix the accent into the panels", x, y, w, column, 8, false,
                (cx, cy) -> ui.sliderWithValue(cx, cy, column, Theme.tint() / (double) Theme.MAX_TINT, Theme.tint() + "%",
                        f -> Theme.setTint((int) Math.round(f * Theme.MAX_TINT))));
        y = row("Corner radius", "Roundness of the window and controls", x, y, w, column, 8, true,
                (cx, cy) -> ui.sliderWithValue(cx, cy, column, Theme.radius() / (double) Theme.MAX_RADIUS,
                        Theme.radius() + "px", f -> Theme.setRadius((int) Math.round(f * Theme.MAX_RADIUS))));

        y = ui.section("Interface", x, y, w);
        y = row("Animations", "Fades and slides in the menu", x, y, w, Theme.CHECK_SIZE, Theme.CHECK_SIZE, false,
                (cx, cy) -> ui.checkbox("animations", cx, cy, Theme.animations(),
                        () -> Theme.setAnimations(!Theme.animations())));
        y = row("Smooth text", "Clean sans font; off uses Minecraft's pixel font", x, y, w, Theme.CHECK_SIZE,
                Theme.CHECK_SIZE, false, (cx, cy) -> ui.checkbox("smooth-text", cx, cy, Theme.smoothText(),
                        () -> Theme.setSmoothText(!Theme.smoothText())));
        y = row("World dim", "How dark the game gets behind the menu", x, y, w, column, 8, false,
                (cx, cy) -> ui.sliderWithValue(cx, cy, column, Theme.dim() / (double) Theme.MAX_DIM, Theme.dim() + "%",
                        f -> Theme.setDim((int) Math.round(f * Theme.MAX_DIM))));
        int scaleW = Math.min(ui.segmentedWidth(SCALE_OPTIONS), Math.round(w * 0.6f));
        y = row("Menu size", "Auto fits the menu to your screen", x, y, w, scaleW, Ui.ROW_H, false,
                (cx, cy) -> ui.segmented(cx, cy, scaleW, Ui.ROW_H, SCALE_OPTIONS, SCALE_OPTIONS.get(Theme.scale()),
                        option -> Theme.setScale(SCALE_OPTIONS.indexOf(option))));
        int bindW = Math.max(36, ui.width("Press a key...") + Icons.SIZE + 14);
        y = row("Open menu", "Also in Minecraft's Controls", x, y, w, bindW, Ui.ROW_H, true,
                (cx, cy) -> ui.bindButton("open-key", cx + bindW, cy, MenuKeys.keyName(), MenuKeys::setKey));

        y = ui.section("Profiles", x, y, w);
        int profilesW = Math.min(ui.segmentedWidth(DaedalusConfig.PROFILES), Math.round(w * 0.6f));
        y = row("Active profile", "Each keeps its own features and keybinds", x, y, w, profilesW, Ui.ROW_H, true,
                (cx, cy) -> ui.segmented(cx, cy, profilesW, Ui.ROW_H, DaedalusConfig.PROFILES,
                        DaedalusConfig.activeProfile(), this::loadProfile));
        ui.endScroll(contentScroll, y + Theme.CONTENT_PAD_Y);
    }

    /** The accent presets: a row of tiles, each a coloured diamond and a name. Returns the y below them. */
    private int presets(int x, int y, int w) {
        int gap = 4;
        int n = Theme.PRESETS.size();
        int tileW = (w - gap * (n - 1)) / n;
        int h = 16;
        for (int i = 0; i < n; i++) {
            Theme.Preset preset = Theme.PRESETS.get(i);
            int tx = x + i * (tileW + gap);
            boolean on = Theme.accentRgb() == preset.color();
            boolean hovered = ui.hover(tx, y, tileW, h);
            ui.box(tx, y, tileW, h, hovered ? Theme.hover() : Theme.card(), on ? Theme.accent() : Theme.line());
            ui.diamond(tx + 8.5f, y + h / 2f, 7, 0xFF000000 | preset.color());
            ui.text(ui.truncate(preset.name(), tileW - 18), tx + 15, Ui.textY(y, h), on ? Theme.accentLight() : Theme.text());
            ui.hit(tx, y, tileW, h, Ui.left(() -> Theme.setAccent(preset.color())));
        }
        return y + h;
    }

    /**
     * A colour swatch and a hex field. Clicking the field selects all of it, so you can type a new colour straight
     * away; it applies as soon as 6 valid hex digits are in.
     */
    private void colorField(String id, int x, int y, int rgb, Consumer<Integer> onColor) {
        ui.box(x, y, Ui.ROW_H, Ui.ROW_H, 0xFF000000 | rgb, Theme.line());
        ui.textField(id, x + Ui.ROW_H + 4, y, 80 - Ui.ROW_H - 4, Ui.ROW_H, Theme.hex(rgb), "#rrggbb", 7, Ui.HEX, text -> {
            int parsed = Theme.parseHex(text);
            if (parsed >= 0) {
                onColor.accept(parsed);
            }
        }, null, true);
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
    // Input. Mouse positions arrive in GUI pixels and are turned into menu pixels. Anything the menu does marks the
    // config as changed; it is saved a moment later, so dragging a slider does not write the file every frame.
    // =====================================================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ui.mouseClicked(event.x() / scale, event.y() / scale, event.button());
        DaedalusConfig.markDirty();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return ui.mouseDragged(event.x() / scale);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ui.mouseReleased();
        DaedalusConfig.markDirty();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ui.mouseScrolled(mouseX / scale, mouseY / scale, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        DaedalusConfig.markDirty();
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
        DaedalusConfig.markDirty();
        if (ui.charTyped(event)) {
            return true;
        }
        // "/" jumps to the search box.
        if (event.codepoint() == '/') {
            ui.focus("search", STATE.query(), 32, Ui.ANY_TEXT, this::setQuery, null, false);
            return true;
        }
        return false;
    }
}
