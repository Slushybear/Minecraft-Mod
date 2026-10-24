package com.slushybear.daedalus.automation;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Macros that ship inside the mod. They are read-only: a player copies one into their own macros to change it.
 * Every step is checked against the code defaults when this loads, so a bad edit to the file fails in tests.
 */
public final class Presets {
    private static final String RESOURCE = "/assets/daedalus/presets/macros.json";
    private static final List<Macro> ALL = load();

    private Presets() {}

    public static List<Macro> all() {
        return ALL;
    }

    private static List<Macro> load() {
        try (InputStream in = Presets.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            List<Macro> presets = new Gson().fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<List<Macro>>() {}.getType());
            for (Macro preset : presets) {
                for (Step step : preset.steps()) {
                    DefinitionValidator.checkStep(step, Allowlist.defaults());
                }
            }
            return List.copyOf(presets);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
