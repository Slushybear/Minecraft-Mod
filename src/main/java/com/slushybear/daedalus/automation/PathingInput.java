package com.slushybear.daedalus.automation;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Movement input the walker sets directly, put in place of the keyboard input while a route is walked. Vanilla reads
 * keyPresses and moveVector from it exactly as it does from KeyboardInput, so a key the player happens to hold cannot
 * fight the walker, and nothing in the options or key bindings is touched.
 */
public final class PathingInput extends ClientInput {
    public boolean forward;
    public boolean back;
    public boolean left;
    public boolean right;
    public boolean jump;
    public boolean sneak;
    public boolean sprint;

    @Override
    public void tick() {
        keyPresses = new Input(forward, back, left, right, jump, sneak, sprint);
        // Same as KeyboardInput: x is left minus right, y is forward minus back, normalised.
        moveVector = new Vec2(impulse(left, right), impulse(forward, back)).normalized();
    }

    public void release() {
        forward = false;
        back = false;
        left = false;
        right = false;
        jump = false;
        sneak = false;
        sprint = false;
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0;
        }
        return positive ? 1 : -1;
    }
}
