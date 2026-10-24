package com.slushybear.daedalus.pathfinding;

/**
 * Everything a search is allowed to do, frozen for the length of one search so a route is never planned under two rule
 * sets at once.
 *
 * @param allowSprint plan flat walking at sprint speed
 * @param allowDiagonal step diagonally between level cells
 * @param allowParkour jump gaps of 1 and 2 blocks
 * @param allowLongJumps also sprint-jump 3-block gaps, jump 4-block gaps with a run-up, curve around obstacles and jump
 *                       to the side. Needs allowParkour
 * @param allowWater wade and swim
 * @param allowLadders climb ladders and vines
 * @param maxFallNoWater highest drop onto solid ground. 3 is the most with no fall damage
 * @param maxFallIntoWater highest drop into water
 * @param maxNodes hard cap on expanded nodes, whatever the time limits say
 * @param primaryTimeoutMs stop after this long once a usable partial route exists
 * @param failureTimeoutMs stop after this long even if nothing usable was found
 * @param profile how fast this player covers ground: every cost that depends on horizontal speed comes from it
 */
public record PathSettings(
        boolean allowSprint,
        boolean allowDiagonal,
        boolean allowParkour,
        boolean allowLongJumps,
        boolean allowWater,
        boolean allowLadders,
        int maxFallNoWater,
        int maxFallIntoWater,
        int maxNodes,
        long primaryTimeoutMs,
        long failureTimeoutMs,
        SpeedProfile profile
) {
    /** Jump models assume vanilla speed. Within this much of it they are close enough to trust. */
    private static final double JUMP_SPEED_MIN = 0.85;
    private static final double LONG_JUMP_SPEED_MAX = 1.15;

    public static PathSettings defaults() {
        return new PathSettings(true, true, true, true, true, true, 3, 20, 200_000, 500, 2000, SpeedProfile.vanilla());
    }

    /**
     * Plans for a player moving at `factor` times vanilla speed. Jumps are only planned where their physics is trusted:
     * the long jumps (which rely on a sprint-jump's reach) only at close to vanilla speed, and plain gap jumps at any speed
     * from a bit under vanilla upward, since the walker slows right down for a jump and takes off at vanilla pace.
     */
    public PathSettings withSpeed(double factor) {
        SpeedProfile next = new SpeedProfile(factor);
        boolean jumps = allowParkour && next.factor() >= JUMP_SPEED_MIN;
        boolean longJumps = allowLongJumps && jumps && next.factor() <= LONG_JUMP_SPEED_MAX;
        return new PathSettings(allowSprint, allowDiagonal, jumps, longJumps, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, maxNodes, primaryTimeoutMs, failureTimeoutMs, next);
    }

    public PathSettings withMaxNodes(int value) {
        return new PathSettings(allowSprint, allowDiagonal, allowParkour, allowLongJumps, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, value, primaryTimeoutMs, failureTimeoutMs, profile);
    }

    public PathSettings withParkour(boolean value) {
        return new PathSettings(allowSprint, allowDiagonal, value, allowLongJumps, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, maxNodes, primaryTimeoutMs, failureTimeoutMs, profile);
    }

    public PathSettings withLongJumps(boolean value) {
        return new PathSettings(allowSprint, allowDiagonal, allowParkour, value, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, maxNodes, primaryTimeoutMs, failureTimeoutMs, profile);
    }

    public PathSettings withDiagonal(boolean value) {
        return new PathSettings(allowSprint, value, allowParkour, allowLongJumps, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, maxNodes, primaryTimeoutMs, failureTimeoutMs, profile);
    }

    /** Time limits that never trigger, so tests stop on the node cap alone and give the same answer every run. */
    public PathSettings withoutTimeLimits() {
        return new PathSettings(allowSprint, allowDiagonal, allowParkour, allowLongJumps, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, maxNodes, Long.MAX_VALUE / 4, Long.MAX_VALUE / 4, profile);
    }

    /**
     * How much to hold the forward input back for a jump, 0 to 1. The jump models are for vanilla pace, so a faster body
     * runs up at the input that gives vanilla speed.
     */
    public double jumpThrottle() {
        return Math.min(1, 1 / profile.factor());
    }

    /** The fewest ticks a flat block can cost under these rules. */
    public double fastestFlat() {
        return allowSprint ? profile.fastest() : profile.walk();
    }
}
