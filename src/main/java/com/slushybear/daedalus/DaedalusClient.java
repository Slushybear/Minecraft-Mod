package com.slushybear.daedalus;

import com.slushybear.daedalus.automation.MacroCommands;
import com.slushybear.daedalus.automation.MacroKeys;
import com.slushybear.daedalus.automation.MacroManager;
import com.slushybear.daedalus.behavior.DamageObserver;
import com.slushybear.daedalus.config.DaedalusConfig;
import com.slushybear.daedalus.module.ModuleRegistry;
import com.slushybear.daedalus.module.WorldGizmos;
import com.slushybear.daedalus.module.combat.CombatTracker;
import com.slushybear.daedalus.sync.DefinitionSync;
import com.slushybear.daedalus.ui.MenuKeys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DaedalusClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Daedalus");

    @Override
    public void onInitializeClient() {
        MenuKeys.init();
        CombatTracker.init();
        WorldGizmos.init();
        ModuleRegistry.init();
        DaedalusConfig.load();
        MacroKeys.init();
        MacroManager.load();
        MacroCommands.register();
        DefinitionSync.start();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MenuKeys.tick(client);
            ModuleRegistry.tick(client);
            DamageObserver.tick(client);
            MacroManager.tick(client);
        });
        // Module keybinds can change module states in game, so save once more on the way out.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DaedalusConfig.save());

        LOGGER.info("Daedalus loaded");
    }
}
