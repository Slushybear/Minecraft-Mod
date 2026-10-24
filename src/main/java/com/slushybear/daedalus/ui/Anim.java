package com.slushybear.daedalus.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Smooth transitions for things like the switch thumb sliding and hover highlights fading. Each animated value has a
 * name and moves from 0 to 1 (or back) over a set number of milliseconds. With animations turned off it jumps straight
 * to the end.
 */
final class Anim {
    private final Map<String, Float> values = new HashMap<>();
    private long lastFrame;
    private float deltaMs;

    /** Call once at the start of each frame. */
    void frame() {
        long now = System.nanoTime();
        deltaMs = lastFrame == 0 ? 0 : Math.min(100f, (now - lastFrame) / 1e6f);
        lastFrame = now;
    }

    /** Moves the named value toward target over about durationMs, and returns where it is now, from 0 to 1. */
    float to(String name, float target, int durationMs) {
        if (!Theme.animations()) {
            values.put(name, target);
            return target;
        }
        float value = values.getOrDefault(name, target);
        float step = deltaMs / durationMs;
        value = value < target ? Math.min(target, value + step) : Math.max(target, value - step);
        values.put(name, value);
        // Ease in and out, so movement starts and stops gently instead of at full speed.
        return value * value * (3 - 2 * value);
    }

    float to(String name, float target) {
        return to(name, target, Theme.HOVER_MS);
    }
}
