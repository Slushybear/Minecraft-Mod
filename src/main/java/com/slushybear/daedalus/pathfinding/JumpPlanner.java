package com.slushybear.daedalus.pathfinding;

/**
 * Decides, tick by tick, when to press jump for a gap jump. It simulates vanilla movement along the jump line from the
 * player's real position and speed, for "jump now" and for "one more step first", and jumps at the last moment that
 * still lands. Jumping late gives the most distance, and the landing check keeps a late jump from overshooting a short
 * landing. A landing only counts if it works at 90%, 100% and 110% of the measured speed, since the server rounds things.
 *
 * <p>Coordinates are along the jump line, relative to the takeoff block's centre: its edge is at 0.5, and the landing
 * block's centre is at {@code cells}.
 */
public final class JumpPlanner {
    // Vanilla movement, per tick. Movement input is scaled by 0.98 before it is applied.
    public static final double GROUND_FRICTION = 0.6 * 0.91;
    public static final double AIR_DRAG = 0.91;
    static final double WALK_ACCEL = 0.1 * 0.98;
    static final double SPRINT_ACCEL = 0.13 * 0.98;
    static final double AIR_ACCEL = 0.02 * 0.98;
    static final double SPRINT_AIR_ACCEL = 0.026 * 0.98;
    static final double JUMP_VELOCITY = 0.42;
    static final double SPRINT_JUMP_BOOST = 0.2;
    public static final double GRAVITY = 0.08;
    public static final double VERTICAL_DRAG = 0.98;
    /** The body is 0.6 wide. */
    public static final double HALF_WIDTH = 0.3;

    private static final int MAX_TICKS = 40;
    private static final double[] SPEED_MARGINS = {0.9, 1.0, 1.1};

    public enum Decision { WAIT, JUMP }

    private JumpPlanner() {}

    /**
     * @param progress how far past the takeoff block's centre the body is, along the jump, in blocks
     * @param speed current speed along the jump, in blocks per tick
     * @param sprinting whether the body is sprinting now. A sprint-jump gets an extra forward boost
     * @param cells landing block distance from the takeoff block: 2 is a 1-block gap
     * @param rise 0 to land level, 1 to land a block higher
     * @param landingLength how many blocks of level ground the landing has along the jump line, at least 1
     */
    public static Decision decide(double progress, double speed, boolean sprinting, int cells, int rise, int landingLength) {
        double accel = sprinting ? SPRINT_ACCEL : WALK_ACCEL;
        double nextProgress = progress + speed + accel;
        double nextSpeed = (speed + accel) * GROUND_FRICTION;
        if (nextProgress - HALF_WIDTH >= 0.5) {
            return Decision.JUMP; // one more step and nothing is under us: this is the last chance
        }
        boolean nowLands = landsRobustly(progress, speed, sprinting, cells, rise, landingLength);
        boolean laterLands = landsRobustly(nextProgress, nextSpeed, sprinting, cells, rise, landingLength);
        return nowLands && !laterLands ? Decision.JUMP : Decision.WAIT;
    }

    static boolean landsRobustly(double progress, double speed, boolean sprinting, int cells, int rise, int landingLength) {
        for (double margin : SPEED_MARGINS) {
            if (Double.isNaN(landing(progress, speed * margin, sprinting, cells, rise, landingLength))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simulates a jump pressed now. Returns where along the line the body comes down on the landing block, or NaN if it
     * falls short, hits the landing block's side, or flies past it.
     */
    public static double landing(double progress, double speed, boolean sprinting, int cells, int rise, int landingLength) {
        double landingStart = cells - 0.5;
        double landingEnd = landingStart + Math.max(1, landingLength);
        double groundAccel = sprinting ? SPRINT_ACCEL : WALK_ACCEL;
        double airAccel = sprinting ? SPRINT_AIR_ACCEL : AIR_ACCEL;

        double x = progress;
        double vx = speed + (sprinting ? SPRINT_JUMP_BOOST : 0);
        double y = 0;
        double vy = JUMP_VELOCITY;
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            boolean jumpTick = tick == 0; // still on the ground for the jump tick, so ground acceleration and friction apply
            vx += jumpTick ? groundAccel : airAccel;
            double nextY = y + vy;
            x += vx;

            boolean overLanding = x + HALF_WIDTH > landingStart && x - HALF_WIDTH < landingEnd;
            boolean overTakeoff = x - HALF_WIDTH < 0.5;
            if (overLanding && vy < 0 && nextY <= rise && y >= rise) {
                return x; // came down onto the landing's top
            }
            if (overLanding && nextY < rise) {
                return Double.NaN; // ran into the landing's side
            }
            if (overTakeoff && vy < 0 && nextY <= 0 && !jumpTick) {
                return Double.NaN; // came back down on the takeoff block: too short
            }
            if (nextY < Math.min(0, rise) - 1) {
                return Double.NaN; // fell into the gap, or past the end of the landing
            }
            y = nextY;
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
            vx *= jumpTick ? GROUND_FRICTION : AIR_DRAG;
        }
        return Double.NaN;
    }
}
