package com.slushybear.daedalus.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** On or off. Shown as a toggle switch. */
public final class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        set(json.getAsBoolean());
    }
}
