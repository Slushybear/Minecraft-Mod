package com.slushybear.daedalus.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Any number of options picked from a list. Shown as chips that turn on and off. */
public final class MultiChoiceSetting extends Setting<Set<String>> {
    private final List<String> options;

    public MultiChoiceSetting(String name, List<String> options, String... defaults) {
        super(name, Set.of(defaults));
        this.options = List.copyOf(options);
    }

    public List<String> options() {
        return options;
    }

    public boolean has(String option) {
        return value.contains(option);
    }

    public void toggle(String option) {
        Set<String> next = new LinkedHashSet<>(value);
        if (!next.remove(option)) {
            next.add(option);
        }
        set(next);
    }

    /** Keeps only real options, so a bad config value cannot add junk. */
    @Override
    public void set(Set<String> newValue) {
        Set<String> clean = new LinkedHashSet<>();
        for (String option : options) {
            if (newValue.contains(option)) {
                clean.add(option);
            }
        }
        value = Set.copyOf(clean);
    }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (String option : options) {
            if (value.contains(option)) {
                array.add(option);
            }
        }
        return array;
    }

    @Override
    public void fromJson(JsonElement json) {
        Set<String> loaded = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray()) {
            loaded.add(element.getAsString());
        }
        set(loaded);
    }
}
