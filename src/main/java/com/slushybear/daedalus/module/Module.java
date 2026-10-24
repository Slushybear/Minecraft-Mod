package com.slushybear.daedalus.module;

import com.slushybear.daedalus.module.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final boolean defaultEnabled;
    private final String defaultKeybind;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private String keybind;
    /** Remembers last tick's key state, so holding the key toggles once instead of every tick. */
    boolean keybindWasDown;

    protected Module(String name, String description, Category category, boolean defaultEnabled) {
        this(name, description, category, defaultEnabled, KeyNames.NONE);
    }

    protected Module(String name, String description, Category category, boolean defaultEnabled, String defaultKeybind) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultEnabled = defaultEnabled;
        this.defaultKeybind = defaultKeybind;
        this.enabled = defaultEnabled;
        this.keybind = defaultKeybind;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** The key that toggles this module in game, or {@link KeyNames#NONE}. */
    public String keybind() {
        return keybind;
    }

    public void setKeybind(String key) {
        keybind = key;
    }

    public List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    /** Adds a setting under the "General" heading with no description. */
    protected <S extends Setting<?>> S addSetting(S setting) {
        settings.add(setting);
        return setting;
    }

    /**
     * Adds a setting under a section heading, with a short description shown under its name.
     * Returns the setting so the module can keep it in a field.
     */
    protected <S extends Setting<?>> S addSetting(String group, String description, S setting) {
        setting.place(group, description);
        return addSetting(setting);
    }

    /** Settings sorted into their section headings. Sections and settings keep the order they were added in. */
    public Map<String, List<Setting<?>>> groups() {
        Map<String, List<Setting<?>>> out = new LinkedHashMap<>();
        for (Setting<?> setting : settings) {
            out.computeIfAbsent(setting.group(), group -> new ArrayList<>()).add(setting);
        }
        return out;
    }

    /** For the menu's search: true if the name or description contains the query, ignoring case. */
    public boolean matches(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return q.isEmpty() || (name + " " + description).toLowerCase(Locale.ROOT).contains(q);
    }

    /** What the Reset button does: every setting back to its default. The keybind and enabled are left alone. */
    public void resetSettings() {
        settings.forEach(Setting::reset);
    }

    /** Everything back to defaults, including enabled and the keybind. Used when a profile has nothing saved for this module. */
    public void resetAll() {
        resetSettings();
        keybind = defaultKeybind;
        setEnabled(defaultEnabled);
    }

    /** Called once at startup, for keybinds and other registration. */
    public void init() {}

    /** Called on the client thread every client tick while the module is enabled. */
    public void tick(Minecraft client) {}

    protected void onEnable() {}

    protected void onDisable() {}
}
