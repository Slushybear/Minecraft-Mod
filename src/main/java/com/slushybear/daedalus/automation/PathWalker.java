package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.motion.Gait;
import com.slushybear.daedalus.motion.LookControl;
import com.slushybear.daedalus.pathfinding.Cell;
import com.slushybear.daedalus.pathfinding.JumpPlanner;
import com.slushybear.daedalus.pathfinding.JumpSim;
import com.slushybear.daedalus.pathfinding.MoveType;
import com.slushybear.daedalus.pathfinding.Moves;
import com.slushybear.daedalus.pathfinding.Path;
import com.slushybear.daedalus.pathfinding.PathSettings;
import com.slushybear.daedalus.pathfinding.PathTracker;
import com.slushybear.daedalus.pathfinding.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Walks one planned route. Each tick it asks the {@link PathTracker} which move is current, checks that move (and, once
 * per move, the next few) is still possible in the live world, then sets the input for that move's type.
 *
 * <p>It never decides what to do when things go wrong. It reports OFF_PATH, TIMED_OUT or BLOCKED, and the caller replans
 * once {@link #safeToCancel} says stopping is safe.
 */
public final class PathWalker {
    public enum Status { RUNNING, ARRIVED, OFF_PATH, TIMED_OUT, BLOCKED }

    /** How many moves ahead to re-check against the live world when a move starts. */
    private static final int VERIFY_AHEAD = 5;
    /** Start the jump for a step up once within this distance, so the body does not jump short and fall back. */
    private static final double CLIMB_JUMP_DISTANCE = 1.2;

    /** How far toward the next cell a corner is cut, at most, in blocks. Inside the cell, so the body never leaves its row. */
    private static final double CORNER_CUT = 0.35;
    /** The eyes look this far ahead along the route, at most, and at most this many cells on. */
    private static final double LOOK_AHEAD_DISTANCE = 4.5;
    private static final int LOOK_AHEAD_CELLS = 3;
    /** The view stays within this many degrees of where the feet are heading, except right before a jump. */
    private static final double MAX_LOOK_OFF_HEADING = 45;
    /** Within this many blocks of a jump's takeoff the view is already along the jump. */
    private static final double PRE_AIM_DISTANCE = 2;
    /** Speeds as a share of full: the last blocks of a walk that ends at the goal, a drop, a turn sharper than SHARP_TURN. */
    private static final double END_SPEED = 0.55;
    private static final double SLOW_END_DISTANCE = 3;
    private static final double DROP_SPEED = 0.8;
    private static final double TURN_SPEED = 0.7;
    private static final double SHARP_TURN = 60;
    /** Pitch for a jump: a slight downward look at the gap and the landing. */
    private static final double JUMP_PITCH = 10;
    /** Speed eases toward its target by at most this much a tick, so it never jumps. */
    private static final double SPEED_CHANGE = 0.06;

    private final Path path;
    private final PathSettings settings;
    private final PathTracker tracker;
    private final LookControl look;
    /** True when this route ends at the goal, so the walk can ease off at the end. A segment's end is not a stop. */
    private final boolean slowAtEnd;
    private double speed = 1;
    private int verifiedMove = -1;
    private int plannedJumpMove = -1;
    private JumpSim.Plan plannedJump;
    private String report = "";

    /**
     * @param look the view's turning state, shared across the walks of one task so a new segment carries on turning
     *             instead of starting from rest
     * @param slowAtEnd whether the route ends at the goal
     */
    public PathWalker(Path path, PathSettings settings, LookControl look, boolean slowAtEnd) {
        this.path = path;
        this.settings = settings;
        this.tracker = new PathTracker(path);
        this.look = look;
        this.slowAtEnd = slowAtEnd;
    }

    public Path path() {
        return path;
    }

    /** What the walker saw when it gave up, for the log. */
    public String report() {
        return report;
    }

    /** The cell the current move is heading for, or null once finished. */
    public Cell target() {
        return tracker.finished() ? null : tracker.to().cell();
    }

    public boolean safeToCancel(LocalPlayer player) {
        return tracker.safeToCancel(player.getX(), player.getY(), player.getZ(), supported(player));
    }

    public Status step(Minecraft client, PathingInput input) {
        input.release();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            return Status.OFF_PATH;
        }

        switch (tracker.update(player.getX(), player.getY(), player.getZ(), supported(player))) {
            case FINISHED -> {
                return Status.ARRIVED;
            }
            case OFF_PATH -> {
                report = describe(player, "left the route");
                return Status.OFF_PATH;
            }
            case TIMED_OUT -> {
                report = describe(player, "took far longer than expected");
                return Status.TIMED_OUT;
            }
            default -> { }
        }

        if (!stillPossible(level, player)) {
            return Status.BLOCKED;
        }

        if (!steer(level, player, input)) {
            report = describe(player, "the jump no longer lands");
            return Status.BLOCKED;
        }
        return Status.RUNNING;
    }

    /** The world can change after planning (a block placed, a door shut). Re-check before walking into it. */
    private boolean stillPossible(ClientLevel level, LocalPlayer player) {
        WorldView live = new WorldView(level);
        boolean safe = safeToCancel(player);
        if (safe && !Moves.isPossible(live, tracker.from().cell(), tracker.to().cell(), settings)) {
            report = describe(player, "the current move is no longer possible");
            return false;
        }
        if (verifiedMove != tracker.moveIndex()) {
            verifiedMove = tracker.moveIndex();
            Cell from = tracker.to().cell();
            for (int k = 0; k < VERIFY_AHEAD; k++) {
                Waypoint next = tracker.ahead(k);
                if (next == null) {
                    break;
                }
                if (safe && !Moves.isPossible(live, from, next.cell(), settings)) {
                    report = describe(player, "a move ahead is no longer possible (" + next.cell() + ")");
                    return false;
                }
                from = next.cell();
            }
        }
        return true;
    }

    /** Sets the input for the current move. False only if a steered jump can no longer be planned before taking off. */
    private boolean steer(ClientLevel level, LocalPlayer player, PathingInput input) {
        Cell a = tracker.from().cell();
        Cell b = tracker.to().cell();
        MoveType type = tracker.to().type();
        if (type == MoveType.NEO || type == MoveType.SIDE_JUMP) {
            return steerPlannedJump(level, player, input, a, b, type);
        }
        if (type == MoveType.WALK || type == MoveType.DIAGONAL || type == MoveType.CLIMB || type == MoveType.DROP) {
            steerOnFoot(level, player, input, a, b, type);
            return true;
        }
        boolean floating = type == MoveType.SWIM || type == MoveType.LADDER;

        double dx = b.x() + 0.5 - player.getX();
        double dz = b.z() + 0.5 - player.getZ();
        double dy = b.y() + (floating ? 0.5 : 0) - player.getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // These moves need the heading to be right, so the view turns at the quick pace, but it still turns: nothing snaps.
        // Hold the view when right on top of the target. A ladder is climbed by jumping, so its heading does not matter.
        double yaw = horizontal > 0.05 && type != MoveType.LADDER ? Gait.yawTo(dx, dz) : player.getYRot();
        double pitch = switch (type) {
            case SWIM -> horizontal > 0.05 ? Math.max(-60, Math.min(60, -Math.toDegrees(Math.atan2(dy, horizontal)))) : 0;
            case LADDER -> dy > 0 ? -15 : 10; // up the ladder, or down it
            default -> JUMP_PITCH; // a glance at the gap
        };
        faceTo(player, yaw, pitch, type == MoveType.LADDER ? LookControl.Pace.NORMAL : LookControl.Pace.QUICK);

        switch (type) {
            case SWIM -> {
                input.forward = horizontal > 0.05;
                input.jump = dy > -0.2; // holding jump keeps the head at the surface, letting go sinks
                input.sneak = dy < -0.4;
                setSprint(player, input, settings.allowSprint());
            }
            case LADDER -> {
                input.jump = dy > 0; // jump climbs; letting go slides down
                setSprint(player, input, false);
            }
            case PARKOUR -> steerJump(player, input, a, b, false);
            case RUN_JUMP -> steerJump(player, input, a, b, true);
            case WALK, DIAGONAL, CLIMB, DROP, NEO, SIDE_JUMP -> { }
        }
        return true;
    }

    /** Eases the view toward a yaw and pitch, one tick's worth. */
    private void faceTo(LocalPlayer player, double yaw, double pitch, LookControl.Pace pace) {
        double[] view = look.step(player.getYRot(), player.getXRot(), yaw, pitch, pace);
        player.setYRot((float) view[0]);
        player.setXRot((float) view[1]);
    }

    /**
     * Walking, stepping up and dropping, the way a person does it. The eyes and the feet each follow a point a set distance
     * ahead along the route ({@link Carrot}), so their targets slide smoothly instead of jumping from cell to cell. The
     * feet's point is close, so the body rounds corners, and it stops at any corner that is not safe to cut and at any
     * spot the next move needs the body at. The eyes' point is far, so the view is already turning into a corner before
     * the body gets there, held within 45 degrees of the feet's heading so the body never has to walk sideways. The view
     * eases toward its target ({@link LookControl}) and the feet go at a speed set by how well the view has lined up
     * ({@link Gait}) and by what is coming: the end of the walk, a drop, a sharp turn.
     */
    private void steerOnFoot(ClientLevel level, LocalPlayer player, PathingInput input, Cell a, Cell b, MoveType type) {
        double px = player.getX();
        double pz = player.getZ();
        double[][] line = routeLine(level, player, a, b, type);
        Carrot.Point feetPoint = Carrot.along(line[0], line[1], line[2], stops(line), FEET_CARROT);
        Carrot.Point eyePoint = Carrot.along(line[0], line[1], line[2], new boolean[line[0].length], EYE_CARROT);

        double facing = player.getYRot();
        double heading = Math.hypot(feetPoint.x() - px, feetPoint.z() - pz) > 0.05
                ? Gait.yawTo(feetPoint.x() - px, feetPoint.z() - pz)
                : facing;

        double[] eyes = eyeAim(px, pz, heading, b, eyePoint);
        double[] view = look.step(facing, player.getXRot(), eyes[0], eyes[1]);
        player.setYRot((float) view[0]);
        player.setXRot((float) view[1]);

        double wanted = wantedSpeed(a, b, px, pz);
        speed += Math.max(-SPEED_CHANGE, Math.min(SPEED_CHANGE, wanted - speed));
        Gait.Drive drive = Gait.drive(view[0], heading, speed);
        input.drive(drive.forward(), drive.left());

        double toCentreX = b.x() + 0.5 - px;
        double toCentreZ = b.z() + 0.5 - pz;
        switch (type) {
            case WALK, DIAGONAL -> {
                // On a ledge a block lower than the target (knocked down a step): jump back up.
                input.jump = player.onGround() && b.y() > player.getY() + 0.5;
                setSprint(player, input, sprintDecision(view[0], heading));
            }
            case CLIMB -> {
                input.jump = (player.onGround() && Math.hypot(toCentreX, toCentreZ) <= CLIMB_JUMP_DISTANCE) || player.onClimbable();
                holdSprint(false);
                setSprint(player, input, false);
            }
            default -> {
                // Walk off, then stop pushing once momentum will carry the body over the landing, so it does not overshoot.
                Vec3 velocity = player.getDeltaMovement();
                double aheadX = toCentreX - velocity.x;
                double aheadZ = toCentreZ - velocity.z;
                if (Math.hypot(aheadX, aheadZ) <= 0.15) {
                    input.drive(0, 0);
                }
                holdSprint(false);
                setSprint(player, input, false);
            }
        }
    }

    /**
     * Sprint, with a memory. Starting needs the view well lined up (and the route going straight). Once started it carries
     * on until the view is clearly off or the next move is not walking, and either change is held for at least 12 ticks.
     * Flipping sprint on and off as the angle crosses one threshold makes the field of view pulse, which looks like a
     * jitter.
     */
    private boolean sprintDecision(double viewYaw, double heading) {
        double angle = Math.abs(LookControl.wrap(heading - viewYaw));
        Waypoint next = tracker.ahead(0);
        boolean groundNext = next == null || isGround(next.type());
        boolean canStart = settings.allowSprint() && speed >= 1 && angle <= Gait.SPRINT_ANGLE && sprintableAhead();
        boolean canKeep = settings.allowSprint() && speed >= 0.9 && angle <= SPRINT_KEEP_ANGLE && groundNext;
        if (sprintTicks > 0) {
            sprintTicks--;
        }
        if (sprinting && !canKeep && sprintTicks == 0) {
            sprinting = false;
            sprintTicks = SPRINT_HOLD_TICKS;
        } else if (!sprinting && canStart && sprintTicks == 0) {
            sprinting = true;
            sprintTicks = SPRINT_HOLD_TICKS;
        }
        if (sprinting && !groundNext) {
            sprinting = false; // a jump, drop or climb is next: stop pushing at once
        }
        return sprinting;
    }

    private void holdSprint(boolean on) {
        sprinting = on;
        sprintTicks = 0;
    }

    /**
     * The line the carrots slide along: the player, then the centre of the current target cell and the cells after it.
     * Rows are x, z, route height, and 1 where the feet must stop (a vertex that is not safe to cut).
     */
    private double[][] routeLine(ClientLevel level, LocalPlayer player, Cell a, Cell b, MoveType type) {
        List<Cell> cells = new ArrayList<>();
        cells.add(b);
        for (int k = 0; k < LINE_CELLS; k++) {
            Waypoint w = tracker.ahead(k);
            if (w == null) {
                break;
            }
            cells.add(w.cell());
        }
        int n = cells.size() + 1;
        double[][] line = new double[4][n];
        line[0][0] = player.getX();
        line[1][0] = player.getZ();
        line[2][0] = b.y();
        for (int i = 0; i < cells.size(); i++) {
            line[0][i + 1] = cells.get(i).x() + 0.5;
            line[1][i + 1] = cells.get(i).z() + 0.5;
            line[2][i + 1] = cells.get(i).y();
        }
        WorldView world = new WorldView(level);
        for (int i = 1; i < n; i++) {
            Cell here = cells.get(i - 1);
            Cell before = i == 1 ? a : cells.get(i - 2);
            MoveType into = i == 1 ? type : tracker.ahead(i - 2).type();
            Waypoint out = tracker.ahead(i - 1);
            boolean stop;
            if (i == 1 && (type == MoveType.DROP || type == MoveType.CLIMB)) {
                stop = true; // the edge to walk off, or the block to jump onto
            } else if (out == null) {
                stop = true; // end of this route
            } else if (!isWalkOrDiagonal(out.type())) {
                stop = true; // the next move needs the body here
            } else {
                stop = !softCorner(world, before, here, out.cell(), into, out.type());
            }
            line[3][i] = stop ? 1 : 0;
        }
        return line;
    }

    private static boolean[] stops(double[][] line) {
        boolean[] stop = new boolean[line[3].length];
        for (int i = 1; i < stop.length; i++) {
            stop[i] = line[3][i] == 1;
        }
        return stop;
    }

    private static boolean isWalkOrDiagonal(MoveType type) {
        return type == MoveType.WALK || type == MoveType.DIAGONAL;
    }

    /**
     * True if the route can be rounded at `here`: it goes straight on, or turns a right-angle corner on level ground with
     * the cell inside the corner open at feet and head height, so cutting it can never clip a block.
     */
    private static boolean softCorner(WorldView world, Cell before, Cell here, Cell after, MoveType into, MoveType out) {
        int d1x = here.x() - before.x();
        int d1z = here.z() - before.z();
        int d2x = after.x() - here.x();
        int d2z = after.z() - here.z();
        boolean level = before.y() == here.y() && here.y() == after.y();
        if (d1x == d2x && d1z == d2z && level) {
            return true; // straight on, same level
        }
        boolean cardinal = Math.abs(d1x) + Math.abs(d1z) == 1 && Math.abs(d2x) + Math.abs(d2z) == 1;
        boolean rightAngle = d1x * d2x + d1z * d2z == 0;
        if (!(cardinal && level && rightAngle && into == MoveType.WALK && out == MoveType.WALK)) {
            return false;
        }
        int inX = after.x() - d1x;
        int inZ = after.z() - d1z;
        return world.terrainAt(inX, here.y(), inZ) == Terrain.AIR && world.terrainAt(inX, here.y() + 1, inZ) == Terrain.AIR;
    }

    /**
     * Where the eyes look, as {yaw, pitch}. Yaw is toward the far carrot, held within 45 degrees of the feet's heading.
     * Before a jump the view is already along the jump. Pitch comes from how much higher or lower the route is ahead than
     * under the current target, never from the body's own height, so it does not bob when the player hops or steps up:
     * level ground gives a slight downward glance, a drop looks down at it, a climb looks up.
     */
    private double[] eyeAim(double px, double pz, double heading, Cell b, Carrot.Point far) {
        double yaw = Math.hypot(far.x() - px, far.z() - pz) > 0.3 ? Gait.yawTo(far.x() - px, far.z() - pz) : heading;
        double off = LookControl.wrap(yaw - heading);
        yaw = heading + Math.max(-MAX_LOOK_OFF_HEADING, Math.min(MAX_LOOK_OFF_HEADING, off));

        Waypoint next = tracker.ahead(0);
        if (next != null && isJump(next.type()) && Math.hypot(b.x() + 0.5 - px, b.z() + 0.5 - pz) < PRE_AIM_DISTANCE) {
            yaw = Gait.yawTo(next.cell().x() - b.x(), next.cell().z() - b.z());
        }

        double horizontal = Math.max(far.travelled(), 2.0);
        double rise = far.y() - b.y();
        double pitch = Math.toDegrees(Math.atan2(EYE_HEIGHT - CHEST_HEIGHT - rise, horizontal));
        return new double[]{yaw, Math.max(-20, Math.min(35, pitch))};
    }

    /**
     * How fast to go, 0 to 1. Full speed in the open, easing off over the last few blocks of a walk that ends at the goal,
     * for a drop, and as a sharp turn gets close.
     */
    private double wantedSpeed(Cell a, Cell b, double px, double pz) {
        double wanted = 1;
        if (tracker.to().type() == MoveType.DROP) {
            wanted = Math.min(wanted, DROP_SPEED);
        }
        if (slowAtEnd) {
            Cell last = path.last();
            double remaining = Math.hypot(last.x() + 0.5 - px, last.z() + 0.5 - pz);
            if (remaining < SLOW_END_DISTANCE) {
                wanted = Math.min(wanted, END_SPEED + (1 - END_SPEED) * Math.max(0, remaining - 0.5) / (SLOW_END_DISTANCE - 0.5));
            }
        }
        Waypoint next = tracker.ahead(0);
        if (next != null && isGround(tracker.to().type()) && isGround(next.type())) {
            double first = Gait.yawTo(b.x() - a.x(), b.z() - a.z());
            double second = Gait.yawTo(next.cell().x() - b.x(), next.cell().z() - b.z());
            if (Math.abs(LookControl.wrap(second - first)) > SHARP_TURN) {
                double near = Math.min(1, Math.hypot(b.x() + 0.5 - px, b.z() + 0.5 - pz) / 1.5);
                wanted = Math.min(wanted, TURN_SPEED + (1 - TURN_SPEED) * near);
            }
        }
        return wanted;
    }

    private static boolean isGround(MoveType type) {
        return type == MoveType.WALK || type == MoveType.DIAGONAL || type == MoveType.CLIMB || type == MoveType.DROP;
    }

    private static boolean isJump(MoveType type) {
        return type == MoveType.PARKOUR || type == MoveType.RUN_JUMP || type == MoveType.NEO || type == MoveType.SIDE_JUMP;
    }

    /**
     * A neo or side jump. The plan is worked out once per move from the live world, then each tick the body faces the
     * next point of it, on the ground and in the air alike, and {@link JumpSim} picks the tick to press jump.
     */
    private boolean steerPlannedJump(ClientLevel level, LocalPlayer player, PathingInput input, Cell a, Cell b, MoveType type) {
        if (plannedJumpMove != tracker.moveIndex()) {
            plannedJumpMove = tracker.moveIndex();
            plannedJump = JumpSim.forMove(new WorldView(level), a, b, type);
        }
        JumpSim.Plan plan = plannedJump;
        if (plan == null) {
            return !player.onGround(); // already in the air: nothing to do but ride it out
        }

        double[] at = plan.local(player.getX(), player.getZ());
        double[] facing = plan.facing(at[0], at[1]);
        faceTo(player, Gait.yawTo(facing[0], facing[1]), JUMP_PITCH, LookControl.Pace.QUICK);
        input.forward = true;
        setSprint(player, input, plan.sprint());

        if (player.onGround() && player.getY() - a.y() < 0.01) {
            Vec3 velocity = player.getDeltaMovement();
            double[] v = plan.localVelocity(velocity.x, velocity.z);
            input.jump = JumpSim.shouldJump(new WorldView(level), plan, new JumpSim.Body(at[0], at[1], v[0], v[1]));
        }
        return true;
    }

    /**
     * A gap jump. Plain jumps for gaps of 1 and 2 blocks land short enough for a narrow landing, so sprinting is only used
     * for gaps of 3 or more and for jumping up. The moment to press jump comes from {@link JumpPlanner}, which simulates
     * the jump from the real position and speed and jumps at the last moment that still lands. A run jump starts a block
     * before the takeoff, and the planner simply waits while the body sprints up to it.
     */
    private void steerJump(LocalPlayer player, PathingInput input, Cell a, Cell b, boolean runUp) {
        int dirX = Integer.signum(b.x() - a.x());
        int dirZ = Integer.signum(b.z() - a.z());
        Cell takeoff = runUp ? new Cell(a.x() + dirX, a.y(), a.z() + dirZ) : a;
        int cells = Math.max(Math.abs(b.x() - takeoff.x()), Math.abs(b.z() - takeoff.z()));
        int rise = b.y() - takeoff.y();

        input.forward = true;
        setSprint(player, input, cells >= 4 || rise > 0);

        double progress = (player.getX() - (takeoff.x() + 0.5)) * dirX + (player.getZ() - (takeoff.z() + 0.5)) * dirZ;
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.x * dirX + velocity.z * dirZ;
        boolean overTakeoff = progress - JumpPlanner.HALF_WIDTH < 0.5;
        if (player.onGround() && overTakeoff) {
            int landing = landingLength(b, dirX, dirZ);
            input.jump = JumpPlanner.decide(progress, speed, player.isSprinting(), cells, rise, landing) == JumpPlanner.Decision.JUMP;
        }
    }

    /** Level cells the route keeps going straight onto after the landing, which is room to come down in. */
    private int landingLength(Cell landing, int dirX, int dirZ) {
        int length = 1;
        Cell previous = landing;
        for (int k = 0; ; k++) {
            Waypoint next = tracker.ahead(k);
            if (next == null || next.type() != MoveType.WALK) {
                return length;
            }
            Cell cell = next.cell();
            if (cell.y() != landing.y() || cell.x() - previous.x() != dirX || cell.z() - previous.z() != dirZ) {
                return length;
            }
            length++;
            previous = cell;
        }
    }

    /**
     * Sprint on flat ground only when the route keeps going the same way, so the body does not carry speed into a turn,
     * an edge or a jump that wants a plain run-up.
     */
    private boolean sprintableAhead() {
        if (!settings.allowSprint()) {
            return false;
        }
        Waypoint next = tracker.ahead(0);
        if (next == null) {
            return false;
        }
        Cell a = tracker.from().cell();
        Cell b = tracker.to().cell();
        Cell c = next.cell();
        boolean straight = c.x() - b.x() == b.x() - a.x() && c.z() - b.z() == b.z() - a.z() && c.y() == b.y();
        return straight && (next.type() == MoveType.WALK || next.type() == MoveType.DIAGONAL);
    }

    /** Letting go of sprint does not stop sprinting in vanilla, so stop it explicitly when it is not wanted. */
    private static void setSprint(LocalPlayer player, PathingInput input, boolean wanted) {
        input.sprint = wanted;
        if (!wanted && player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    private static boolean supported(LocalPlayer player) {
        return player.onGround() || player.isInWater() || player.onClimbable();
    }

    private String describe(LocalPlayer player, String what) {
        Waypoint to = tracker.finished() ? null : tracker.to();
        String move = to == null ? "end of route"
                : tracker.from().cell() + " -> " + to.cell() + " (" + to.type() + ", expected " + Math.round(to.cost()) + " ticks)";
        return String.format("%s: player at %.2f %.2f %.2f, move %d of %d: %s",
                what, player.getX(), player.getY(), player.getZ(), tracker.moveIndex() + 1, path.size() - 1, move);
    }
}
