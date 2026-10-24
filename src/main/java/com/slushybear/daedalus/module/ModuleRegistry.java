package com.slushybear.daedalus.module;

import com.slushybear.daedalus.module.combat.DamageNumbers;
import com.slushybear.daedalus.module.combat.HitIndicator;
import com.slushybear.daedalus.module.combat.TargetHud;
import com.slushybear.daedalus.module.visuals.ArrayListHud;
import com.slushybear.daedalus.module.visuals.BlockEsp;
import com.slushybear.daedalus.module.visuals.CameraTweaks;
import com.slushybear.daedalus.module.visuals.CustomInventory;
import com.slushybear.daedalus.module.visuals.Freecam;
import com.slushybear.daedalus.module.visuals.Freelook;
import com.slushybear.daedalus.module.visuals.Fullbright;
import com.slushybear.daedalus.module.visuals.MobEsp;
import com.slushybear.daedalus.config.DaedalusConfig;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class ModuleRegistry {
    // Modules that game code (the mixins) needs to ask about get their own field.
    public static final Fullbright FULLBRIGHT = new Fullbright();
    public static final Freecam FREECAM = new Freecam();
    public static final Freelook FREELOOK = new Freelook();
    public static final CameraTweaks CAMERA = new CameraTweaks();
    public static final MobEsp MOB_ESP = new MobEsp();
    public static final CustomInventory CUSTOM_INVENTORY = new CustomInventory();

    // Sidebar order within each category. Module names are also the keys in config/daedalus.json, so renaming one
    // resets its saved settings. Mining and Farming are empty for now: that automation lives in the Macros tab as
    // MINE, HARVEST and REPLANT steps. Macros has no modules; that tab is the macro editor.
    private static final List<Module> MODULES = List.of(
            new HitIndicator(),
            new DamageNumbers(),
            new TargetHud(),

            new FullStopModule(),
            new PathfinderModule(),

            new ArrayListHud(),
            new BlockEsp(),
            MOB_ESP,
            FULLBRIGHT,
            FREECAM,
            FREELOOK,
            CAMERA,
            CUSTOM_INVENTORY
    );

    private ModuleRegistry() {}

    public static void init() {
        for (Module module : MODULES) {
            module.init();
        }
    }

    public static void tick(Minecraft client) {
        // Module keybinds only work while playing, not while a menu or chat is open.
        boolean playing = client.gui.screen() == null && client.player != null;
        for (Module module : MODULES) {
            boolean down = playing && KeyNames.isDown(client, module.keybind());
            if (down && !module.keybindWasDown) {
                module.toggle();
                DaedalusConfig.markDirty();
            }
            module.keybindWasDown = down;

            if (module.enabled()) {
                module.tick(client);
            }
        }
    }

    public static List<Module> all() {
        return MODULES;
    }

    public static List<Module> in(Category category) {
        return MODULES.stream().filter(m -> m.category() == category).toList();
    }

    /** Modules whose name or description contains the query, ignoring case. */
    public static List<Module> search(String query) {
        return MODULES.stream().filter(m -> m.matches(query)).toList();
    }

    public static long enabledCount(List<Module> modules) {
        return modules.stream().filter(Module::enabled).count();
    }
}
