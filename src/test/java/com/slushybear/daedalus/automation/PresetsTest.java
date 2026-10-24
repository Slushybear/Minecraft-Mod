package com.slushybear.daedalus.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresetsTest {
    @Test
    void everyPresetIsValidUnderTheCodeDefaults() {
        assertFalse(Presets.all().isEmpty());
        Allowlist defaults = Allowlist.defaults();
        for (Macro preset : Presets.all()) {
            assertFalse(preset.steps().isEmpty(), preset.name());
            assertTrue(preset.steps().size() <= defaults.maxSteps(), preset.name());
            for (Step step : preset.steps()) {
                DefinitionValidator.checkStep(step, defaults);
            }
        }
    }

    @Test
    void copyKeepsStepsAndGetsAFreshName() {
        Macro preset = Presets.all().getFirst();
        List<Macro> existing = new ArrayList<>();

        Macro first = DefinitionValidator.copyPreset(preset, Allowlist.defaults(), existing);
        existing.add(first);
        Macro second = DefinitionValidator.copyPreset(preset, Allowlist.defaults(), existing);

        assertEquals(preset.name(), first.name());
        assertEquals(preset.name() + "-2", second.name());
        assertEquals(preset.steps(), first.steps());
        assertEquals(0, first.keySlot());
        assertFalse(first.paused());
    }

    @Test
    void copyRefusesWhenTheListIsFull() {
        Macro preset = Presets.all().getFirst();
        List<Macro> full = new ArrayList<>();
        for (int i = 0; i < Allowlist.defaults().maxMacros(); i++) {
            full.add(new Macro("m" + i, List.of(), 0, 0, false));
        }
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionValidator.copyPreset(preset, Allowlist.defaults(), full));
    }
}
