package com.slushybear.daedalus.pathfinding;

/**
 * How fast this particular player covers ground, worked out from their movement speed. Everything that depends on
 * horizontal speed (walking, sprinting, hopping) scales with it, and everything that does not (falling, climbing a
 * ladder, how long a jump stays in the air) stays fixed, so the search naturally picks different routes at different
 * speeds: with a big speed stat a detour on flat ground beats a jump or a climb, and with none a sprint-jump chain can
 * beat plain sprinting.
 *
 * <p>The factor is the movement speed attribute over vanilla's 0.1, with the sprint bonus left out: 1 is an unmodified
 * player, 5 is 500% walking speed.
 *
 * <p>Hopping is sprint-jumping over and over on level ground. The horizontal speed a jump carries is kept in the air, so
 * how fast a hop chain goes comes from simulating vanilla's movement, not from a formula. At vanilla speed it beats
 * sprinting by about a fifth. As speed goes up, the ground gets faster but the air does not, so past some speed sprinting
 * wins and hopping is dropped.
 */
public final class SpeedProfile {
    /** Blocks per tick at vanilla walking speed, at terminal velocity. 4.317 blocks a second. */
    public static final double BASE_WALK = 4.317 / 20;
    public static final double BASE_SPRINT = 5.612 / 20;
    /** A hop chain must beat sprinting by at least this much to be worth the risk of a missed jump. */
    private static final double HOP_ADVANTAGE = 1.04;
    private static final int HOP_CYCLES = 60;

    private final double factor;
    private final double walkTicks;
    private final double sprintTicks;
    private final double hopTicks;

    public SpeedProfile(double factor) {
        this.factor = Math.max(0.2, Math.min(20, factor));
        this.walkTicks = 1 / (BASE_WALK * this.factor);
        this.sprintTicks = 1 / (BASE_SPRINT * this.factor);
        double hopSpeed = hopBlocksPerTick(this.factor);
        this.hopTicks = hopSpeed > BASE_SPRINT * this.factor * HOP_ADVANTAGE ? 1 / hopSpeed : Double.POSITIVE_INFINITY;
    }

    public static SpeedProfile vanilla() {
        return new SpeedProfile(1);
    }

    public double factor() {
        return factor;
    }

    /** Ticks to walk one block. */
    public double walk() {
        return walkTicks;
    }

    /** Ticks to sprint one block. */
    public double sprint() {
        return sprintTicks;
    }

    /** Ticks per block hopping, or infinity if hopping is not faster than sprinting at this speed. */
    public double hop() {
        return hopTicks;
    }

    /** Walking half a block to the edge and 0.3 more until the body tips off it, at this speed. */
    public double walkOffEdge() {
        return walkTicks * 0.8;
    }

    /** The rest of the block after landing, to get back to the centre, at this speed. */
    public double centerAfterFall() {
        return walkTicks * 0.2;
    }

    public boolean hops() {
        return Double.isFinite(hopTicks);
    }

    /** The fewest ticks any flat move can take per block: the lower bound the heuristic uses. */
    public double fastest() {
        return Math.min(sprintTicks, hopTicks);
    }

    /**
     * Average blocks per tick of a long chain of sprint-jumps on level ground, with the jump pressed the moment the body
     * lands. Vanilla movement: on the jump tick the body gets ground acceleration, the sprint boost and ground friction,
     * and after that air acceleration and air drag, until it comes back down to the floor.
     */
    static double hopBlocksPerTick(double factor) {
        double groundAccel = JumpPlanner.SPRINT_ACCEL * factor;
        double speed = groundAccel / (1 - JumpPlanner.GROUND_FRICTION) * JumpPlanner.GROUND_FRICTION; // terminal, after friction
        double distance = 0;
        int ticks = 0;
        for (int cycle = 0; cycle < HOP_CYCLES; cycle++) {
            double x = 0;
            double y = 0;
            double vx = speed + JumpPlanner.SPRINT_JUMP_BOOST;
            double vy = JumpPlanner.JUMP_VELOCITY;
            int air = 0;
            for (int tick = 0; tick < 40; tick++) {
                vx += tick == 0 ? groundAccel : JumpPlanner.SPRINT_AIR_ACCEL;
                x += vx;
                y += vy;
                air++;
                vx *= tick == 0 ? JumpPlanner.GROUND_FRICTION : JumpPlanner.AIR_DRAG;
                vy = (vy - JumpPlanner.GRAVITY) * JumpPlanner.VERTICAL_DRAG;
                if (vy < 0 && y + vy <= 0) {
                    x += vx; // the tick it lands still carries the body forward
                    air++;
                    break;
                }
            }
            speed = vx * JumpPlanner.AIR_DRAG;
            if (cycle >= HOP_CYCLES / 2) {
                distance += x;
                ticks += air;
            }
        }
        return distance / ticks;
    }
}
