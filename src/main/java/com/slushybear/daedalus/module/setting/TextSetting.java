package com.slushybear.daedalus.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A short line of text. Shown as a text field. */
public final class TextSetting extends Setting<String> {
    public static final int MAX_LENGTH = 64;

    public TextSetting(String name, String defaultValue) {
        super(name, defaultValue);
    }

    @Override
    public void set(String newValue) {
        value = newValue.length() > MAX_LENGTH ? newValue.substring(0, MAX_LENGTH) : newValue;
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
