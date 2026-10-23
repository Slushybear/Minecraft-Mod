package com.slushybear.daedalus.pathfinding;

/**
 * Everything a search is allowed to do, frozen for the length of one search so a route is never planned under two rule
 * sets at once.
 *
 * @param allowSprint plan flat walking at sprint speed
 * @param allowDiagonal step diagonally between level cells
 * @param allowParkour jump gaps of 1 and 2 blocks
 * @param allowLongJumps also sprint-jump 3-block gaps. Needs allowParkour
 * @param allowWater wade and swim
 * @param allowLadders climb ladders and vines
 * @param maxFallNoWater highest drop onto solid ground. 3 is the most with no fall damage
 * @param maxFallIntoWater highest drop into water
 * @param maxNodes hard cap on expanded nodes, whatever the time limits say
 * @param primaryTimeoutMs stop after this long once a usable partial route exists
 * @param failureTimeoutMs stop after this long even if nothing usable was found
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
        long failureTimeoutMs
) {
    public static PathSettings defaults() {
        return new PathSettings(true, true, true, true, true, true, 3, 20, 200_000, 500, 2000);
    }

    public PathSettings withMaxNodes(int value) {
        return new PathSettings(allowSprint, allowDiagonal, allowParkour, allowLongJumps, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, value, primaryTimeoutMs, failureTimeoutMs);
    }

    public PathSettings withParkour(boolean value) {
        return new PathSettings(allowSprint, allowDiagonal, value, allowLongJumps, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, maxNodes, primaryTimeoutMs, failureTimeoutMs);
    }

    public PathSettings withLongJumps(boolean value) {
        return new PathSettings(allowSprint, allowDiagonal, allowParkour, value, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, maxNodes, primaryTimeoutMs, failureTimeoutMs);
    }

    public PathSettings withDiagonal(boolean value) {
        return new PathSettings(allowSprint, value, allowParkour, allowLongJumps, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, maxNodes, primaryTimeoutMs, failureTimeoutMs);
    }

    /** Time limits that never trigger, so tests stop on the node cap alone and give the same answer every run. */
    public PathSettings withoutTimeLimits() {
        return new PathSettings(allowSprint, allowDiagonal, allowParkour, allowLongJumps, allowWater, allowLadders,
                maxFallNoWater, maxFallIntoWater, maxNodes, Long.MAX_VALUE / 4, Long.MAX_VALUE / 4);
    }
}
