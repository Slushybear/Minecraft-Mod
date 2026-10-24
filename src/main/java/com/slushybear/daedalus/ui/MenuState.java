package com.slushybear.daedalus.ui;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the menu is showing: which sidebar category is open, which feature is selected, which page the content panel
 * shows, and the search text. It has no drawing code; DaedalusScreen reads it and calls its methods.
 *
 * Module values are not stored here. They live on the modules, so nothing is lost when the menu closes.
 *
 * Key rule: selecting a feature (clicking its name in the sidebar) and enabling it (the switch in the header) are
 * separate actions, and neither ever does the other.
 */
final class MenuState {
    /** What the content panel shows. */
    enum Page { MODULE, MACROS, SETTINGS }

    private final Supplier<List<Module>> modules;
    /** Only one category is open at a time (an accordion). Null means all are closed. */
    private Category expanded;
    private Module selected;
    private Page page = Page.MODULE;
    private String query = "";

    MenuState(Supplier<List<Module>> modules) {
        this.modules = modules;
        // Start on the first feature so the content panel is never empty.
        modules.get().stream().findFirst().ifPresent(this::selectModule);
    }

    // ---- Sidebar categories ----

    /**
     * Clicking a category header opens it and closes whichever one was open, or closes it if it was already open.
     * It never changes the selected feature. Opening Macros also shows the macro editor, since Macros has no features.
     */
    void toggleCategory(Category category) {
        expanded = expanded == category ? null : category;
        if (category == Category.MACROS && expanded == Category.MACROS) {
            page = Page.MACROS;
        }
    }

    /** Whether a category's features are shown. While searching, every category with a match is open. */
    boolean isExpanded(Category category) {
        return searching() ? !modulesUnder(category).isEmpty() : expanded == category;
    }

    /** Features listed under a category in the sidebar, filtered by the search. */
    List<Module> modulesUnder(Category category) {
        return inCategory(category).stream().filter(m -> m.matches(query)).toList();
    }

    List<Module> inCategory(Category category) {
        return modules.get().stream().filter(m -> m.category() == category).toList();
    }

    /** Categories to draw. While searching, ones with no matches are hidden. */
    List<Category> visibleCategories() {
        return Arrays.stream(Category.values())
                .filter(c -> !searching() || !modulesUnder(c).isEmpty())
                .toList();
    }

    /** The "2" in "2/3" next to a category: how many of its features are on. */
    int enabledCount(Category category) {
        return (int) inCategory(category).stream().filter(Module::enabled).count();
    }

    /** The "3" in "2/3": how many features the category has. */
    int totalCount(Category category) {
        return inCategory(category).size();
    }

    /** True when a search matches nothing, so the sidebar shows "No features match". */
    boolean noResults() {
        return searching() && visibleCategories().isEmpty();
    }

    // ---- Selection and pages ----

    /** A feature was clicked in the sidebar: show its settings and open its category. Never enables it. */
    void selectModule(Module module) {
        selected = module;
        expanded = module.category();
        page = Page.MODULE;
    }

    void openMacros() {
        expanded = Category.MACROS;
        page = Page.MACROS;
    }

    void openSettings() {
        page = Page.SETTINGS;
    }

    /** The header switch: turns the selected feature on or off. Never changes the selection. */
    void toggleSelected() {
        if (selected != null) {
            selected.toggle();
        }
    }

    /** The header reset button: the selected feature's settings back to defaults. Its keybind is kept. */
    void resetSelected() {
        if (selected != null) {
            selected.resetSettings();
        }
    }

    // ---- Search ----

    void setQuery(String text) {
        query = text == null ? "" : text;
    }

    boolean searching() {
        return !query.isBlank();
    }

    // ---- Getters ----

    Page page() {
        return page;
    }

    Module selected() {
        return selected;
    }

    Category expanded() {
        return expanded;
    }

    String query() {
        return query;
    }
}
