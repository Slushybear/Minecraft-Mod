package com.slushybear.daedalus.automation;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Waits for a number of ticks, optionally holding one key down for that time. Used by WAIT and HOLD. */
final class TimedTask implements Task {
    private final KeyMapping key;
    private int ticksLeft;
    private boolean pressed;

    TimedTask(KeyMapping key, int ticks) {
        this.key = key;
        this.ticksLeft = ticks;
    }

    @Override
    public boolean tick(Minecraft client) {
        if (key != null && !pressed) {
            key.setDown(true);
            pressed = true;
        }
        if (--ticksLeft <= 0) {
            cancel(client);
            return true;
        }
        return false;
    }

    @Override
    public void cancel(Minecraft client) {
        if (key != null && pressed) {
            key.setDown(false);
            pressed = false;
        }
    }
}
