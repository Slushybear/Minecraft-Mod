package com.slushybear.daedalus.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Smooth transitions for things like the toggle knob sliding and hover highlights fading. Each animated value has a name;
 * every frame it moves a bit closer to its target. With animations turned off it jumps straight there.
 */
final class Anim {
    private static final float SPEED = 14f;

    private final Map<String, Float> values = new HashMap<>();
    private long lastFrame;
    private float delta;

    /** Call once at the start of each frame. */
    void frame() {
        long now = System.nanoTime();
        delta = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
    }

    /** Moves the named value toward target and returns where it is now, from 0 to 1. */
    float to(String name, float target) {
        if (!Theme.animations()) {
            values.put(name, target);
            return target;
        }
        float value = values.getOrDefault(name, target);
        value += (target - value) * Math.min(1f, delta * SPEED);
        if (Math.abs(target - value) < 0.01f) {
            value = target;
        }
        values.put(name, value);
        return value;
    }
}
