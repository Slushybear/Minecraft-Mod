package com.slushybear.daedalus.module.setting;

import com.google.gson.JsonElement;

/**
 * One option on a module, like a toggle or a slider. Holds the current value and the default that Reset goes back to.
 * Each type knows how to turn its value into JSON and back, so the config file can save it.
 *
 * The group is the section heading the menu shows it under (for example "Scanning"), and the description is the
 * grey line under its name. Both are set by Module.addSetting.
 */
public abstract class Setting<T> {
    public static final String DEFAULT_GROUP = "General";

    private final String name;
    private final T defaultValue;
    protected T value;
    private String group = DEFAULT_GROUP;
    private String description = "";

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public String group() {
        return group;
    }

    public String description() {
        return description;
    }

    /** Called once by Module.addSetting. */
    public void place(String group, String description) {
        this.group = group;
        this.description = description;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        value = newValue;
    }

    public void reset() {
        set(defaultValue);
    }

    public abstract JsonElement toJson();

    /** Reads a value saved by {@link #toJson()}. May throw if the file was edited by hand; the caller catches it. */
    public abstract void fromJson(JsonElement json);
}
