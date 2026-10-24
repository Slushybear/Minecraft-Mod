package com.slushybear.daedalus.automation;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Movement input the walker sets directly, put in place of the keyboard input while a route is walked. Vanilla reads
 * keyPresses and moveVector from it exactly as it does from KeyboardInput, so a key the player happens to hold cannot
 * fight the walker, and nothing in the options or key bindings is touched.
 *
 * <p>Movement is either the on/off keys, or, after {@link #drive}, an analog pair like a gamepad stick: forward and left
 * between -1 and 1, so the body can go at an angle to the view and at less than full speed.
 */
public final class PathingInput extends ClientInput {
    /** Below this a stick counts as centred, as in vanilla. */
    private static final double DEAD_ZONE = 1.0E-5;

    public boolean forward;
    public boolean back;
    public boolean left;
    public boolean right;
    public boolean jump;
    public boolean sneak;
    public boolean sprint;

    private boolean analog;
    private double driveForward;
    private double driveLeft;

    /** Analog movement for this tick: each between -1 and 1, and the pair no longer than 1. */
    public void drive(double forwardAmount, double leftAmount) {
        analog = true;
        driveForward = forwardAmount;
        driveLeft = leftAmount;
    }

    @Override
    public void tick() {
        if (analog) {
            keyPresses = new Input(driveForward > DEAD_ZONE, driveForward < -DEAD_ZONE, driveLeft > DEAD_ZONE,
                    driveLeft < -DEAD_ZONE, jump, sneak, sprint);
            moveVector = new Vec2((float) driveLeft, (float) driveForward);
            return;
        }
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
        analog = false;
        driveForward = 0;
        driveLeft = 0;
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0;
        }
        return positive ? 1 : -1;
    }
}
