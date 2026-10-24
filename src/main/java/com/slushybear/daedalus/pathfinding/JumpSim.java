package com.slushybear.daedalus.pathfinding;

/**
 * Jumps that are not a straight line: a neo, which curves out around a wall standing in the jump's path and back in, and
 * a side jump to a landing one block off to the side. Unlike {@link JumpPlanner}, which works along one line, this
 * simulates vanilla movement in two dimensions with the body's real 0.6 x 1.8 box against the world, steering the way the
 * walker will: each tick it faces the next point of the plan it has not yet passed.
 *
 * <p>The search uses it to decide whether a jump is possible at all, from a standstill on the takeoff block. The walker
 * uses it every tick to decide when to press jump, from the real position and speed. Both press jump at the last tick
 * that still lands at 90%, 100% and 110% of the speed, like JumpPlanner.
 *
 * <p>Positions are in the takeoff's frame: f runs forward along the jump's main direction and l to the side, both
 * measured from the takeoff block's centre. Heights are world heights.
 */
public final class JumpSim {
    private static final double HALF_WIDTH = JumpPlanner.HALF_WIDTH;
    private static final double HEIGHT = 1.8;
    /** Boxes that only touch do not collide, as in vanilla. */
    private static final double EPS = 1e-4;
    private static final int MAX_TICKS = 40;
    private static final double[] SPEED_MARGINS = {0.9, 1.0, 1.1};
    /**
     * Steering lines a neo tries, each {how far out to the side, how far before the wall's near face to aim out there,
     * how far past its far face to keep aiming out there}. Found by sweeping the simulation over walls 1 and 2 long at
     * every place in gaps of 1 to 3: between them these three land every case that any line lands. All need sprinting.
     */
    private static final double[][] NEO_LINES = {{0.7, 0.8, -0.4}, {0.9, 0.4, 0.0}, {0.5, 1.3, -0.3}};
    /** A plan point counts as passed once the body is this close to it, along the jump. */
    private static final double PASSED = 0.05;

    /**
     * One jump. `via` are points to steer through in order, each {f, l}. After the last one the body steers for the
     * landing block's centre.
     */
    public record Plan(Cell takeoff, int dirX, int dirZ, Cell landing, double[][] via, boolean sprint) {
        int perpX() {
            return -dirZ;
        }

        int perpZ() {
            return dirX;
        }

        double landingF() {
            return (landing.x() - takeoff.x()) * dirX + (landing.z() - takeoff.z()) * dirZ;
        }

        double landingL() {
            return (landing.x() - takeoff.x()) * perpX() + (landing.z() - takeoff.z()) * perpZ();
        }

        /** Where the body is, in this plan's frame: {f, l}. */
        public double[] local(double x, double z) {
            double rx = x - (takeoff.x() + 0.5);
            double rz = z - (takeoff.z() + 0.5);
            return new double[]{rx * dirX + rz * dirZ, rx * perpX() + rz * perpZ()};
        }

        /** A velocity turned into this plan's frame: {forward, sideways}. */
        public double[] localVelocity(double vx, double vz) {
            return new double[]{vx * dirX + vz * dirZ, vx * perpX() + vz * perpZ()};
        }

        /** The direction to face, as a world {x, z} vector, for a body at {f, l}. */
        public double[] facing(double f, double l) {
            double[] aim = aim(this, f, l);
            return new double[]{aim[0] * dirX + aim[1] * perpX(), aim[0] * dirZ + aim[1] * perpZ()};
        }
    }

    /** A body standing on the takeoff's level, in a plan's frame. Velocity is what is left after last tick's friction. */
    public record Body(double f, double l, double vf, double vl) {}

    private JumpSim() {}

    /**
     * A neo from `takeoff` to `landing` straight ahead along an axis, around whatever stands in the way. Tries each
     * steering line on both sides and returns the first that lands from a standstill, or null.
     */
    public static Plan neo(BlockView world, Cell takeoff, Cell landing) {
        int dirX = Integer.signum(landing.x() - takeoff.x());
        int dirZ = Integer.signum(landing.z() - takeoff.z());
        if ((dirX == 0) == (dirZ == 0)) {
            return null;
        }
        int distance = Math.abs(landing.x() - takeoff.x()) + Math.abs(landing.z() - takeoff.z());
        int first = -1;
        int last = -1;
        for (int k = 1; k < distance; k++) {
            if (laneBlocked(world, takeoff, dirX, dirZ, k, 0)) {
                first = first < 0 ? k : first;
                last = k;
            }
        }
        if (first < 0) {
            return null; // nothing in the way: a straight jump
        }
        for (double[] line : NEO_LINES) {
            for (int side : new int[]{1, -1}) {
                double[][] via = {
                        {first - 0.5 - line[1], side * line[0]},
                        {last + 0.5 + line[2], side * line[0]}};
                Plan plan = new Plan(takeoff, dirX, dirZ, landing, via, true);
                if (landsFromStandstill(world, plan)) {
                    return plan;
                }
            }
        }
        return null;
    }

    /** A jump to a landing one block to the side of the jump's main direction, steering straight for it. Or null. */
    public static Plan side(BlockView world, Cell takeoff, Cell landing) {
        int ax = landing.x() - takeoff.x();
        int az = landing.z() - takeoff.z();
        boolean alongX = Math.abs(ax) > Math.abs(az);
        if ((alongX ? Math.abs(az) : Math.abs(ax)) != 1) {
            return null;
        }
        int dirX = alongX ? Integer.signum(ax) : 0;
        int dirZ = alongX ? 0 : Integer.signum(az);
        for (boolean sprint : new boolean[]{false, true}) {
            Plan plan = new Plan(takeoff, dirX, dirZ, landing, new double[0][], sprint);
            if (landsFromStandstill(world, plan)) {
                return plan;
            }
        }
        return null;
    }

    /** The plan for a planned move, worked out again from the world, or null if it no longer works. */
    public static Plan forMove(BlockView world, Cell from, Cell to, MoveType type) {
        return switch (type) {
            case NEO -> neo(world, from, to);
            case SIDE_JUMP -> side(world, from, to);
            default -> null;
        };
    }

    /**
     * Whether to press jump now, for a body on the ground. True at the last tick that still lands, and also when the
     * next step would leave the takeoff with no jump at all, since that is the last chance.
     */
    public static boolean shouldJump(BlockView world, Plan plan, Body body) {
        Body next = groundStep(world, plan, body);
        return next == null || (landsRobustly(world, plan, body) && !landsRobustly(world, plan, next));
    }

    /** Runs up from a standstill on the takeoff block, the way the walker would, and reports whether the jump lands. */
    static boolean landsFromStandstill(BlockView world, Plan plan) {
        Body body = new Body(0, 0, 0, 0);
        boolean nowLands = landsRobustly(world, plan, body);
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            Body next = groundStep(world, plan, body);
            if (next == null) {
                return nowLands;
            }
            boolean laterLands = landsRobustly(world, plan, next);
            if (nowLands && !laterLands) {
                return true;
            }
            body = next;
            nowLands = laterLands;
        }
        return false;
    }

    static boolean landsRobustly(BlockView world, Plan plan, Body body) {
        for (double margin : SPEED_MARGINS) {
            if (!lands(world, plan, body, margin)) {
                return false;
            }
        }
        return true;
    }

    /** One tick of running on the takeoff's level without jumping. Null if that walks into something or off the edge. */
    static Body groundStep(BlockView world, Plan plan, Body body) {
        double accel = plan.sprint() ? JumpPlanner.SPRINT_ACCEL : JumpPlanner.WALK_ACCEL;
        double[] aim = aim(plan, body.f(), body.l());
        double vf = body.vf() + aim[0] * accel;
        double vl = body.vl() + aim[1] * accel;
        double f = body.f() + vf;
        double l = body.l() + vl;
        int y = plan.takeoff().y();
        if (collides(world, plan, f, l, y) || !standsOn(world, plan, f, l, y)) {
            return null;
        }
        return new Body(f, l, vf * JumpPlanner.GROUND_FRICTION, vl * JumpPlanner.GROUND_FRICTION);
    }

    /** Simulates a jump pressed now, with the horizontal speed scaled by `margin`. True if it comes down on the landing. */
    static boolean lands(BlockView world, Plan plan, Body body, double margin) {
        boolean sprint = plan.sprint();
        double f = body.f();
        double l = body.l();
        double y = plan.takeoff().y();
        double vf = body.vf() * margin;
        double vl = body.vl() * margin;
        double vy = JumpPlanner.JUMP_VELOCITY;
        int landingY = plan.landing().y();
        double lowest = Math.min(plan.takeoff().y(), landingY) - 1.5;

        double[] aim = aim(plan, f, l);
        if (sprint) {
            vf += aim[0] * JumpPlanner.SPRINT_JUMP_BOOST;
            vl += aim[1] * JumpPlanner.SPRINT_JUMP_BOOST;
        }
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            boolean jumpTick = tick == 0; // still on the ground for the jump tick: ground acceleration and friction
            double accel = jumpTick
                    ? (sprint ? JumpPlanner.SPRINT_ACCEL : JumpPlanner.WALK_ACCEL)
                    : (sprint ? JumpPlanner.SPRINT_AIR_ACCEL : JumpPlanner.AIR_ACCEL);
            aim = aim(plan, f, l);
            vf += aim[0] * accel;
            vl += aim[1] * accel;
            double nf = f + vf;
            double nl = l + vl;
            double ny = y + vy;

            if (vy < 0) {
                // Coming down through a whole-block height with something under the feet there means landing on it.
                for (int h = (int) Math.floor(y); h > ny; h--) {
                    if (standsOn(world, plan, nf, nl, h)) {
                        return h == landingY && overLanding(plan, nf, nl) && !collides(world, plan, nf, nl, h);
                    }
                }
            }
            if (collides(world, plan, nf, nl, ny) || ny < lowest) {
                return false;
            }
            f = nf;
            l = nl;
            y = ny;
            vy = (vy - JumpPlanner.GRAVITY) * JumpPlanner.VERTICAL_DRAG;
            double friction = jumpTick ? JumpPlanner.GROUND_FRICTION : JumpPlanner.AIR_DRAG;
            vf *= friction;
            vl *= friction;
        }
        return false;
    }

    /** The unit direction, {f, l}, from the body to the next plan point it has not passed, or to the landing. */
    static double[] aim(Plan plan, double f, double l) {
        double tf = plan.landingF();
        double tl = plan.landingL();
        for (double[] point : plan.via()) {
            if (point[0] > f + PASSED) {
                tf = point[0];
                tl = point[1];
                break;
            }
        }
        double df = tf - f;
        double dl = tl - l;
        double length = Math.sqrt(df * df + dl * dl);
        return length < 1e-9 ? new double[]{1, 0} : new double[]{df / length, dl / length};
    }

    /** True if the lane cell k forward and j to the side has anything at feet, head or jump height. */
    static boolean laneBlocked(BlockView world, Cell takeoff, int dirX, int dirZ, int k, int j) {
        int x = takeoff.x() + dirX * k - dirZ * j;
        int z = takeoff.z() + dirZ * k + dirX * j;
        for (int y = takeoff.y(); y <= takeoff.y() + 2; y++) {
            if (!passable(world.terrainAt(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    private static boolean overLanding(Plan plan, double f, double l) {
        double lf = plan.landingF();
        double ll = plan.landingL();
        return f + HALF_WIDTH > lf - 0.5 && f - HALF_WIDTH < lf + 0.5 && l + HALF_WIDTH > ll - 0.5 && l - HALF_WIDTH < ll + 0.5;
    }

    /** True if the box with its feet at height y has something solid right under it. */
    private static boolean standsOn(BlockView world, Plan plan, double f, double l, int y) {
        for (int k = lo(f); k <= hi(f); k++) {
            for (int j = lo(l); j <= hi(l); j++) {
                if (solid(world, plan, k, j, y - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean collides(BlockView world, Plan plan, double f, double l, double y) {
        int bottom = (int) Math.floor(y + EPS);
        int top = (int) Math.floor(y + HEIGHT - EPS);
        for (int k = lo(f); k <= hi(f); k++) {
            for (int j = lo(l); j <= hi(l); j++) {
                for (int h = bottom; h <= top; h++) {
                    if (solid(world, plan, k, j, h)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** First and last cell index the body's width overlaps, around a centre coordinate. */
    private static int lo(double centre) {
        return (int) Math.floor(centre - HALF_WIDTH + EPS + 0.5);
    }

    private static int hi(double centre) {
        return (int) Math.floor(centre + HALF_WIDTH - EPS + 0.5);
    }

    /**
     * Whether a cell stops the body. Everything that is not open counts, lava and fire included, since flying into them
     * is as bad as hitting a wall. A fence or wall below reaches half a block up into the cell above, so that cell counts
     * too.
     */
    private static boolean solid(BlockView world, Plan plan, int k, int j, int h) {
        int x = plan.takeoff().x() + plan.dirX() * k + plan.perpX() * j;
        int z = plan.takeoff().z() + plan.dirZ() * k + plan.perpZ() * j;
        return !passable(world.terrainAt(x, h, z)) || world.terrainAt(x, h - 1, z) == Terrain.BLOCKED;
    }

    private static boolean passable(Terrain terrain) {
        return terrain == Terrain.AIR || terrain == Terrain.WATER || terrain == Terrain.CLIMBABLE;
    }
}
