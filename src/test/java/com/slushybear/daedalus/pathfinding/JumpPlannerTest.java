package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Runs a whole jump the way the walker does: hold forward from a standstill, ask the planner each tick, and once it says
 * jump, fly the rest. The physics is vanilla's, so these are claims about what will happen in game, not just about the
 * planner agreeing with itself. A model is still a model: the in-game smoke test is what confirms them.
 */
class JumpPlannerTest {
    /**
     * @return where the body lands along the jump line, or NaN if it misses
     */
    private static double runAndJump(double startProgress, boolean sprinting, int cells, int rise, int landingLength) {
        double x = startProgress;
        double speed = 0;
        double accel = sprinting ? JumpPlanner.SPRINT_ACCEL : JumpPlanner.WALK_ACCEL;
        for (int tick = 0; tick < 200; tick++) {
            if (JumpPlanner.decide(x, speed, sprinting, cells, rise, landingLength) == JumpPlanner.Decision.JUMP) {
                assertTrue(x - JumpPlanner.HALF_WIDTH < 0.5, "jumped from x=" + x + ", which is no longer over the takeoff block");
                return JumpPlanner.landing(x, speed, sprinting, cells, rise, landingLength);
            }
            double moved = speed + accel;
            x += moved;
            speed = moved * JumpPlanner.GROUND_FRICTION;
        }
        return Double.NaN;
    }

    @Test
    void plainJumpOverAOneBlockGapOntoOneBlock() {
        assertFalse(Double.isNaN(runAndJump(0, false, 2, 0, 1)));
    }

    @Test
    void plainJumpOverATwoBlockGapOntoOneBlock() {
        assertFalse(Double.isNaN(runAndJump(0, false, 3, 0, 1)));
    }

    @Test
    void plainJumpOverATwoBlockGapWithARunUp() {
        assertFalse(Double.isNaN(runAndJump(-2, false, 3, 0, 4)));
    }

    @Test
    void sprintJumpOverAThreeBlockGap() {
        assertFalse(Double.isNaN(runAndJump(0, true, 4, 0, 3)));
    }

    @Test
    void sprintJumpUpOneBlockOverAOneBlockGap() {
        assertFalse(Double.isNaN(runAndJump(0, true, 2, 1, 2)));
    }

    @Test
    void sprintJumpUpOneBlockOverATwoBlockGap() {
        assertFalse(Double.isNaN(runAndJump(0, true, 3, 1, 3)));
    }

    @Test
    void plainJumpCannotClearAThreeBlockGap() {
        // The planner still jumps at the last moment, but nothing lands: this is why 3-block gaps need sprinting.
        assertTrue(Double.isNaN(runAndJump(0, false, 4, 0, 3)));
    }

    @Test
    void nothingClearsAFourBlockGap() {
        assertTrue(Double.isNaN(runAndJump(0, true, 5, 0, 3)));
    }

    @Test
    void aSprintJumpOvershootsASingleBlockAfterAOneBlockGap() {
        // Jumping straight away at sprint speed flies over a one-block landing. The planner avoids this by not
        // sprinting for short gaps.
        assertTrue(Double.isNaN(JumpPlanner.landing(0.35, 0.2, true, 2, 0, 1)));
    }

    @Test
    void waitsWhileThereIsStillRoomToRunUp() {
        assertTrue(JumpPlanner.decide(-1.5, 0, false, 2, 0, 1) == JumpPlanner.Decision.WAIT);
    }

    @Test
    void jumpsOnTheLastTickBeforeRunningOutOfBlock() {
        // At 0.7 past centre with walking speed, the next step leaves the takeoff block entirely.
        assertTrue(JumpPlanner.decide(0.7, 0.2, false, 2, 0, 1) == JumpPlanner.Decision.JUMP);
    }
}
