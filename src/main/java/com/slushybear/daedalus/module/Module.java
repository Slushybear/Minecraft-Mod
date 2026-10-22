package com.slushybear.daedalus.module;

import net.minecraft.client.Minecraft;

public abstract class Module {
    private final String name;
    private final String description;
    private boolean enabled;

    protected Module(String name, String description, boolean defaultEnabled) {
        this.name = name;
        this.description = description;
        this.enabled = defaultEnabled;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
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

    /** Called once at startup, for keybinds and other registration. */
    public void init() {}

    /** Called on the client thread every client tick while the module is enabled. */
    public void tick(Minecraft client) {}

    protected void onEnable() {}

    protected void onDisable() {}
}
