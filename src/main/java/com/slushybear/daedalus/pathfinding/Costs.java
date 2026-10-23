package com.slushybear.daedalus.pathfinding;

/**
 * Movement costs in game ticks (1/20 s), worked out from vanilla speeds. Using real time for every move means walking,
 * jumping, falling and swimming compare fairly, and a cost doubles as the time a move should take.
 */
public final class Costs {
    /** Walking speed is 4.317 blocks per second. */
    public static final double WALK = 20 / 4.317;
    /** Sprinting speed is 5.612 blocks per second. */
    public static final double SPRINT = 20 / 5.612;
    /** Wading through water without swimming, about 2.2 blocks per second. */
    public static final double WADE = 20 / 2.2;
    /** Sprint-swimming, about 2.86 blocks per second. */
    public static final double SWIM = 20 / 2.86;
    public static final double LADDER_UP = 20 / 2.35;
    public static final double LADDER_DOWN = 20 / 3.0;
    /** Walking half a block to the edge and 0.3 more until the body tips off it. */
    public static final double WALK_OFF_EDGE = WALK * 0.8;
    /** The rest of the block after landing, to get back to the centre. */
    public static final double CENTER_AFTER_FALL = WALK - WALK_OFF_EDGE;
    /** A jump peaks 1.25 blocks up. Rising one block takes the time to fall 1.25 minus the time to fall the last 0.25. */
    public static final double JUMP_UP = fallTicks(1.25) - fallTicks(0.25);
    /** Every jump costs a little extra, so a route without jumps wins when it is about as fast. */
    public static final double JUMP_PENALTY = 2;
    /** Stepping into a cell beside lava or fire. A preference, not a ban: DANGER cells themselves are never entered. */
    public static final double NEAR_DANGER = 10;
    /** Longest run of fully submerged steps, a stand-in for how long the player can hold their breath. */
    public static final int MAX_SUBMERGED = 20;

    /**
     * Lower bounds per block of distance, for heuristics. Sprinting is the cheapest way to cover ground, so no move covers
     * a block sideways for less. Every upward move spends at least this much per block on top of its sideways cost
     * (a step up is the tightest at about 3.07), and every downward move at least this much (a long fall into water is
     * the tightest). GoalAndCostTest checks these against real routes.
     */
    public static final double MIN_PER_BLOCK_FLAT = SPRINT;
    public static final double MIN_PER_BLOCK_UP = 3.0;
    public static final double MIN_PER_BLOCK_DOWN = 1.0;

    private static final double[] FALL = new double[257];

    static {
        for (int i = 0; i < FALL.length; i++) {
            FALL[i] = fallTicks(i);
        }
    }

    private Costs() {}

    /** Ticks to fall a whole number of blocks from a standstill. */
    public static double fall(int blocks) {
        return FALL[Math.min(blocks, FALL.length - 1)];
    }

    /**
     * Ticks to fall a distance from a standstill, using vanilla's gravity: each tick the body moves by its current
     * vertical speed, then the speed loses 0.08 and keeps 98% of itself.
     */
    public static double fallTicks(double blocks) {
        if (blocks <= 0) {
            return 0;
        }
        double fallen = 0;
        double speed = 0;
        for (int tick = 0; ; tick++) {
            double step = -speed;
            if (step > 0 && fallen + step >= blocks) {
                return tick + (blocks - fallen) / step;
            }
            fallen += step;
            speed = (speed - 0.08) * 0.98;
        }
    }
}
