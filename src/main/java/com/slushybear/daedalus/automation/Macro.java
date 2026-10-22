package com.slushybear.daedalus.automation;

import java.util.List;

/**
 * A player-built macro. Runs its steps in order each time it is triggered.
 *
 * @param intervalTicks 0 means manual only, otherwise it runs again every this many ticks
 * @param keySlot 0 means no key, otherwise 1 to Limits.KEY_SLOTS for a bound key
 */
public record Macro(String name, List<Step> steps, int intervalTicks, int keySlot, boolean paused) {
    public Macro withSteps(List<Step> value) {
        return new Macro(name, List.copyOf(value), intervalTicks, keySlot, paused);
    }

    public Macro withKeySlot(int value) {
        return new Macro(name, steps, intervalTicks, value, paused);
    }

    public Macro withPaused(boolean value) {
        return new Macro(name, steps, intervalTicks, keySlot, value);
    }
}
