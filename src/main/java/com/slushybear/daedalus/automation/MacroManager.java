package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.DaedalusClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;

/** Owns the macro list, the interval countdowns and the running macros. Only the client thread touches these. */
public final class MacroManager {
    private static final List<Macro> MACROS = new ArrayList<>();
    /** Ticks left until each interval macro runs again, keyed by lowercase name. */
    private static final Map<String, Integer> COUNTDOWNS = new HashMap<>();
    private static final Map<String, MacroRunner> RUNNING = new HashMap<>();
    private static volatile Allowlist allowlist = Allowlist.defaults();

    private MacroManager() {}

    public static void load() {
        MACROS.clear();
        MACROS.addAll(MacroStore.load());
    }

    public static List<Macro> all() {
        return List.copyOf(MACROS);
    }

    public static Allowlist allowlist() {
        return allowlist;
    }

    /** Called from the definition sync thread. Reads of the volatile field are safe. */
    public static void setAllowlist(Allowlist value) {
        allowlist = value;
    }

    public static Optional<Macro> find(String name) {
        return MACROS.stream().filter(m -> m.name().equalsIgnoreCase(name)).findFirst();
    }

    public static boolean isRunning(String name) {
        return RUNNING.containsKey(key(name));
    }

    public static void add(Macro macro) {
        MACROS.add(macro);
        MacroStore.save(MACROS);
    }

    /** Applies a change to the named macro and saves. Returns false if no macro has that name. */
    public static boolean update(String name, UnaryOperator<Macro> change) {
        for (int i = 0; i < MACROS.size(); i++) {
            Macro macro = MACROS.get(i);
            if (macro.name().equalsIgnoreCase(name)) {
                MACROS.set(i, change.apply(macro));
                MacroStore.save(MACROS);
                return true;
            }
        }
        return false;
    }

    public static boolean remove(String name) {
        boolean removed = MACROS.removeIf(m -> m.name().equalsIgnoreCase(name));
        if (removed) {
            stop(name);
            COUNTDOWNS.remove(key(name));
            MacroStore.save(MACROS);
        }
        return removed;
    }

    /** Binds the macro to a key slot. Slot 0 unbinds. A slot can only belong to one macro. */
    public static void bind(String name, int slot) {
        if (slot != 0) {
            MACROS.replaceAll(m -> m.keySlot() == slot && !m.name().equalsIgnoreCase(name) ? m.withKeySlot(0) : m);
        }
        update(name, m -> m.withKeySlot(slot));
    }

    /**
     * Starts a macro now. Throws IllegalStateException if it is paused, empty or already running.
     */
    public static void start(Macro macro) {
        if (macro.paused()) {
            throw new IllegalStateException("'" + macro.name() + "' is paused");
        }
        if (macro.steps().isEmpty()) {
            throw new IllegalStateException("'" + macro.name() + "' has no steps yet");
        }
        String key = key(macro.name());
        if (RUNNING.containsKey(key)) {
            throw new IllegalStateException("'" + macro.name() + "' is already running");
        }
        RUNNING.put(key, new MacroRunner(macro));
    }

    public static boolean stop(String name) {
        MacroRunner runner = RUNNING.remove(key(name));
        if (runner == null) {
            return false;
        }
        runner.cancel(Minecraft.getInstance());
        return true;
    }

    /** Starts whichever macro is bound to the pressed key slot. */
    public static void startBySlot(int slot) {
        MACROS.stream()
                .filter(m -> m.keySlot() == slot && !m.paused())
                .findFirst()
                .ifPresent(macro -> {
                    try {
                        start(macro);
                    } catch (IllegalStateException e) {
                        DaedalusClient.LOGGER.warn("Macro key {} did not start: {}", slot, e.getMessage());
                    }
                });
    }

    public static void tick(Minecraft client) {
        // Menus stop every macro, so a held key is never stuck down behind a screen.
        if (client.player == null || client.level == null) {
            return;
        }
        if (client.gui.screen() != null) {
            cancelAll(client);
            return;
        }

        MacroKeys.poll();
        scheduleIntervals();

        int budget = Limits.MAX_INSTANT_STEPS_PER_TICK;
        Iterator<MacroRunner> runners = RUNNING.values().iterator();
        while (runners.hasNext()) {
            MacroRunner runner = runners.next();
            budget -= runner.tick(client, Math.max(budget, 0));
            if (runner.finished()) {
                runners.remove();
            }
        }
    }

    private static void scheduleIntervals() {
        for (Macro macro : MACROS) {
            if (macro.paused() || macro.intervalTicks() == 0) {
                continue;
            }
            String key = key(macro.name());
            int remaining = COUNTDOWNS.getOrDefault(key, 0) - 1;
            if (remaining > 0) {
                COUNTDOWNS.put(key, remaining);
                continue;
            }
            COUNTDOWNS.put(key, macro.intervalTicks());
            if (!RUNNING.containsKey(key) && !macro.steps().isEmpty()) {
                start(macro);
            }
        }
    }

    private static void cancelAll(Minecraft client) {
        RUNNING.values().forEach(runner -> runner.cancel(client));
        RUNNING.clear();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
