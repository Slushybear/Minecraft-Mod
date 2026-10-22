package com.slushybear.daedalus.module;

import java.util.List;
import net.minecraft.client.Minecraft;

public final class ModuleRegistry {
    private static final List<Module> MODULES = List.of(
            new FullStopModule()
    );

    private ModuleRegistry() {}

    public static void init() {
        for (Module module : MODULES) {
            module.init();
        }
    }

    public static void tick(Minecraft client) {
        for (Module module : MODULES) {
            if (module.enabled()) {
                module.tick(client);
            }
        }
    }

    public static List<Module> all() {
        return MODULES;
    }
}
