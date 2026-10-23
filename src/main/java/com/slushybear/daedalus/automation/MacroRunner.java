package com.slushybear.daedalus.automation;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Runs one macro's steps in order. A timed, walking or mining step holds the macro until it finishes. */
public final class MacroRunner {
    private final Macro macro;
    private int index;
    private Task active;
    private boolean finished;

    public MacroRunner(Macro macro) {
        this.macro = macro;
    }

    public boolean finished() {
        return finished;
    }

    public Macro macro() {
        return macro;
    }

    /** Stops the run from outside, such as when the player takes damage, and tells the player why. */
    public void interrupt(Minecraft client, String reason) {
        say(client, "stopped: " + reason);
        cancel(client);
    }

    /** Runs tasks until the budget is spent or an ongoing task is still going. Returns how many tasks were started. */
    public int tick(Minecraft client, int budget) {
        int started = 0;
        while (!finished) {
            if (active == null) {
                if (index >= macro.steps().size()) {
                    finished = true;
                    break;
                }
                if (started >= budget) {
                    break;
                }
                active = StepExecutor.start(client, macro.steps().get(index++));
                started++;
            }
            if (!active.tick(client)) {
                break;
            }
            if (active.failed()) {
                say(client, "stopped: " + active.failure());
                cancel(client);
                break;
            }
            active = null;
        }
        return started;
    }

    /** Stops the run and releases anything the current task is holding. */
    public void cancel(Minecraft client) {
        if (active != null) {
            active.cancel(client);
            active = null;
        }
        finished = true;
    }

    private void say(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Daedalus] " + macro.name() + " " + message));
        }
    }
}
