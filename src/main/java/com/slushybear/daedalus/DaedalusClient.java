package com.slushybear.daedalus;

import com.slushybear.daedalus.automation.MacroCommands;
import com.slushybear.daedalus.automation.MacroKeys;
import com.slushybear.daedalus.automation.MacroManager;
import com.slushybear.daedalus.module.ModuleRegistry;
import com.slushybear.daedalus.sync.DefinitionSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DaedalusClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Daedalus");

    @Override
    public void onInitializeClient() {
        ModuleRegistry.init();
        MacroKeys.init();
        MacroManager.load();
        MacroCommands.register();
        DefinitionSync.start();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ModuleRegistry.tick(client);
            MacroManager.tick(client);
        });
    }
}
