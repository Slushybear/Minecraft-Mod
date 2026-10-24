package com.slushybear.daedalus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.SliderSetting;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The menu's rules from the spec: the accordion, selecting versus enabling, search, counts and reset. */
class MenuStateTest {
    private static final class TestModule extends Module {
        final BoolSetting smooth = addSetting("Camera", "Ease the camera", new BoolSetting("Smooth", true));
        final SliderSetting factor = addSetting("Camera", "How far to zoom", new SliderSetting("Factor", 4, 1, 10, 0.5));

        TestModule(String name, String description, Category category) {
            super(name, description, category, false);
        }
    }

    private final TestModule hit = new TestModule("Hit indicator", "Flashes the crosshair on hit", Category.COMBAT);
    private final TestModule esp = new TestModule("Block ESP", "Highlights ores", Category.VISUALS);
    private final TestModule fullbright = new TestModule("Fullbright", "Max brightness everywhere", Category.VISUALS);
    private final List<Module> modules = List.of(hit, esp, fullbright);

    private MenuState state() {
        return new MenuState(() -> modules);
    }

    @Test
    void startsOnTheFirstFeatureWithItsCategoryOpen() {
        MenuState state = state();
        assertSame(hit, state.selected());
        assertTrue(state.isExpanded(Category.COMBAT));
        assertEquals(MenuState.Page.MODULE, state.page());
    }

    @Test
    void onlyOneCategoryIsOpenAtATime() {
        MenuState state = state();
        state.toggleCategory(Category.VISUALS);
        assertTrue(state.isExpanded(Category.VISUALS));
        assertFalse(state.isExpanded(Category.COMBAT));
        state.toggleCategory(Category.VISUALS);
        assertNull(state.expanded());
    }

    @Test
    void openingACategoryNeverChangesTheSelection() {
        MenuState state = state();
        state.toggleCategory(Category.VISUALS);
        assertSame(hit, state.selected());
    }

    @Test
    void selectingNeverEnablesAndEnablingNeverSelects() {
        MenuState state = state();
        state.selectModule(esp);
        assertFalse(esp.enabled());
        assertTrue(state.isExpanded(Category.VISUALS));
        state.toggleSelected();
        assertTrue(esp.enabled());
        assertSame(esp, state.selected());
    }

    @Test
    void openingMacrosShowsTheEditor() {
        MenuState state = state();
        state.toggleCategory(Category.MACROS);
        assertEquals(MenuState.Page.MACROS, state.page());
        state.selectModule(fullbright);
        assertEquals(MenuState.Page.MODULE, state.page());
    }

    @Test
    void searchOpensEveryCategoryWithAMatchAndHidesTheRest() {
        MenuState state = state();
        state.setQuery("bright");
        assertEquals(List.of(Category.VISUALS), state.visibleCategories());
        assertTrue(state.isExpanded(Category.VISUALS));
        assertEquals(List.of(fullbright), state.modulesUnder(Category.VISUALS));

        state.setQuery("crosshair");
        assertTrue(state.isExpanded(Category.COMBAT));
        assertFalse(state.noResults());

        state.setQuery("nothing like this");
        assertTrue(state.noResults());
    }

    @Test
    void countsShowHowManyAreOn() {
        MenuState state = state();
        fullbright.setEnabled(true);
        assertEquals(1, state.enabledCount(Category.VISUALS));
        assertEquals(2, state.totalCount(Category.VISUALS));
        assertEquals(0, state.totalCount(Category.MINING));
    }

    @Test
    void resetRestoresSettingsButKeepsTheKeybind() {
        MenuState state = state();
        state.selectModule(esp);
        esp.smooth.set(false);
        esp.factor.set(9.0);
        esp.setKeybind("key.keyboard.z");
        state.resetSelected();
        assertTrue(esp.smooth.get());
        assertEquals(4.0, esp.factor.get());
        assertEquals("key.keyboard.z", esp.keybind());
    }

    @Test
    void settingsAreGroupedInTheOrderTheyWereAdded() {
        assertEquals(List.of("Camera"), List.copyOf(esp.groups().keySet()));
        assertEquals(List.of(esp.smooth, esp.factor), esp.groups().get("Camera"));
    }
}
