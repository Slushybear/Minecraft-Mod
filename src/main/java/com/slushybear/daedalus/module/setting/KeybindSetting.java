package com.slushybear.daedalus.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A key or mouse button, stored by its Minecraft name such as "key.keyboard.g". See {@link com.slushybear.daedalus.module.KeyNames}. */
public final class KeybindSetting extends Setting<String> {
    public KeybindSetting(String name, String defaultKey) {
        super(name, defaultKey);
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
