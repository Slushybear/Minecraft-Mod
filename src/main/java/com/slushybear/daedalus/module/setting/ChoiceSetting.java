package com.slushybear.daedalus.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;

/** One option picked from a short list. Shown as a row of segmented buttons. */
public final class ChoiceSetting extends Setting<String> {
    private final List<String> options;

    public ChoiceSetting(String name, String defaultValue, String... options) {
        super(name, defaultValue);
        this.options = List.of(options);
    }

    public List<String> options() {
        return options;
    }

    /** Ignores anything that is not one of the options, so a bad config value cannot break the setting. */
    @Override
    public void set(String newValue) {
        if (options.contains(newValue)) {
            value = newValue;
        }
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        set(json.getAsString());
    }
}
