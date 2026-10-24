package com.slushybear.daedalus.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.function.Function;

/**
 * A short line of text. Shown as a text field.
 *
 * Typing goes through {@link #edit}: text that passes the check is applied straight away. Text that fails stays in the
 * field as a draft, with an error under it, and the setting keeps its last good value until the text is fixed.
 */
public final class TextSetting extends Setting<String> {
    public static final int MAX_LENGTH = 64;

    /** Returns an error message for bad text, or null when the text is fine. */
    private final Function<String, String> check;
    private String draft;
    private String draftError;

    public TextSetting(String name, String defaultValue) {
        this(name, defaultValue, text -> null);
    }

    public TextSetting(String name, String defaultValue, Function<String, String> check) {
        super(name, defaultValue);
        this.check = check;
    }

    /** What the user typed. Applies it if it is valid, otherwise keeps it as a draft with an error. */
    public void edit(String text) {
        String error = text.length() > MAX_LENGTH ? "Max " + MAX_LENGTH + " characters" : check.apply(text);
        if (error == null) {
            value = text;
            draft = null;
            draftError = null;
        } else {
            draft = text;
            draftError = error;
        }
    }

    /** The text to show in the field: the invalid draft if there is one, otherwise the value. */
    public String shown() {
        return draft != null ? draft : value;
    }

    /** Why the draft was rejected, or null when there is no bad draft. */
    public String draftError() {
        return draftError;
    }

    @Override
    public void set(String newValue) {
        value = newValue.length() > MAX_LENGTH ? newValue.substring(0, MAX_LENGTH) : newValue;
        draft = null;
        draftError = null;
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
