package com.slushybear.daedalus.module;

import com.slushybear.daedalus.module.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    protected <S extends Setting<?>> S addSetting(S setting) {
        settings.add(setting);
        return setting;
    }

    /** What the Reset button does: settings and keybind back to defaults. Enabled is left alone. */
    public void resetSettings() {
        settings.forEach(Setting::reset);
        keybind = defaultKeybind;
    }

    /** Everything back to defaults, including enabled. Used when a profile has nothing saved for this module. */
    public void resetAll() {
        resetSettings();
        setEnabled(defaultEnabled);
    }

    /** Called once at startup, for keybinds and other registration. */
    public void init() {}

    /** Called on the client thread every client tick while the module is enabled. */
    public void tick(Minecraft client) {}

    protected void onEnable() {}

    protected void onDisable() {}
}
