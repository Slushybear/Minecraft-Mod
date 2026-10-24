package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.motion.Carrot;
import com.slushybear.daedalus.motion.FallPredictor;
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
import com.slushybear.daedalus.pathfinding.SpeedProfile;
import com.slushybear.daedalus.pathfinding.Terrain;
import com.slushybear.daedalus.pathfinding.Waypoint;
import java.util.ArrayList;
import java.util.List;
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

    /** The feet follow a point this far ahead along the route, so corners are rounded. The eyes follow one further on. */
    private static final double FEET_CARROT = 1.1;
    private static final double EYE_CARROT = 3.5;
    /** How many cells past the current target the carrot line includes. */
    private static final int LINE_CELLS = 5;
    /** Standing eye height, and the height of what is looked at (chest of a person ahead), for pitch. */
    private static final double EYE_HEIGHT = 1.62;
    private static final double CHEST_HEIGHT = 1.2;
    /** Sprint is held through turns up to this many degrees off the view, and every change is held this many ticks. */
    private static final double SPRINT_KEEP_ANGLE = 35;
    private static final int SPRINT_HOLD_TICKS = 12;
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
    private static final double SPEED_UP = 0.06;
    /** Slowing down is allowed to be quicker, since braking for a stop at high speed cannot wait. */
    private static final double SPEED_DOWN = 0.25;
    /** Blocks a body coasts after letting go of the input, per block-per-tick of speed: friction leaves about 1.2 of it. */
    private static final double COAST = 1.2;
    /** Braking never asks for less than this share of full speed. */
    private static final double BRAKE_FLOOR = 0.25;
    /** A hop chain needs this many straight, level cells ahead of the current target, all with room overhead. */
    private static final int HOP_STRAIGHT_CELLS = 3;
    /** Hopping starts only when the view is this close to the heading. */
    private static final double HOP_ALIGNED_ANGLE = 12;
    /** Hopping stops this many blocks before the end of a route that ends at the goal, so the body can settle there. */
    private static final double HOP_END_CLEARANCE = 6;
    /** Closer than this to the feet's carrot, the heading is held instead of following it. */
    private static final double HEADING_HOLD_DISTANCE = 0.35;
    /** In the air during a drop: a predicted landing this close to the target is left alone, and this far off gets full correction. */
    private static final double FALL_TOLERANCE = 0.1;
    private static final double FALL_FULL_CORRECTION = 0.4;
    /** On a ladder, drift further than this from the column's centre is pushed back, at most this hard. */
    private static final double LADDER_CENTRE_TOLERANCE = 0.2;
    private static final double LADDER_CENTRE_PUSH = 0.35;
    /** Swimming: the view pitches at most this far, and a carrot closer than this means the move is straight up or down. */
    private static final double SWIM_PITCH_LIMIT = 45;
    private static final double SWIM_HOLD_DISTANCE = 0.15;

    private final Path path;
    private final PathSettings settings;
    private final PathTracker tracker;
    private final LookControl look;
    /** True when this route ends at the goal, so the walk can ease off at the end. A segment's end is not a stop. */
    private final boolean slowAtEnd;
    private double speed = 1;
    private boolean sprinting;
    private int sprintTicks;
    private int verifiedMove = -1;
    private int loggedMove = -1;
    private BodyState lastState;
    private boolean hopping;
    private boolean braking;
    /** The feet's last heading, held when the carrot is too close to give a steady direction (arriving at a stop). */
    private double lastHeading = Double.NaN;
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

    /** Index of the current move: from waypoint moveIndex() to the next. */
    public int moveIndex() {
        return tracker.moveIndex();
    }

    /** Moves left after the current one. */
    public int movesLeft() {
        return path.size() - 2 - tracker.moveIndex();
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

        logChanges(player);
        if (!stillPossible(level, player)) {
            return Status.BLOCKED;
        }

        if (!steer(level, player, input)) {
            report = describe(player, "the jump no longer lands");
            return Status.BLOCKED;
        }
        return Status.RUNNING;
    }

    /** What the body is doing, for logging transitions between walking, jumping or falling, swimming and climbing. */
    private enum BodyState { GROUND, AIR, WATER, LADDER }

    /** Logs a new move starting and a change of body state. Only on a change, so this is quiet tick to tick. */
    private void logChanges(LocalPlayer player) {
        if (loggedMove != tracker.moveIndex()) {
            loggedMove = tracker.moveIndex();
            Waypoint to = tracker.to();
            PathLog.debug("Move {}/{}: {} -> {} {} (expect {} ticks, {} resyncs so far)", loggedMove + 1, path.size() - 1,
                    cellText(tracker.from().cell()), cellText(to.cell()), to.type(), Math.round(to.cost()), tracker.resyncs());
        }
        BodyState state = player.onClimbable() ? BodyState.LADDER
                : player.isInWater() ? BodyState.WATER
                : player.onGround() ? BodyState.GROUND
                : BodyState.AIR;
        if (state != lastState) {
            // A hop chain flips between ground and air every jump; that is logged as the hop starting and stopping instead.
            boolean hopFlip = hopping && (state == BodyState.AIR || state == BodyState.GROUND)
                    && (lastState == BodyState.AIR || lastState == BodyState.GROUND);
            if (lastState != null && !hopFlip) {
                PathLog.debug("Body {} -> {} at {} during {} (speed {})", lastState, state,
                        String.format("%.2f %.2f %.2f", player.getX(), player.getY(), player.getZ()), tracker.to().type(),
                        String.format("%.3f b/t", player.getDeltaMovement().horizontalDistance()));
            }
            lastState = state;
        }
    }

    private static String cellText(Cell cell) {
        return cell.x() + " " + cell.y() + " " + cell.z();
    }

    /** The world can change after planning (a block placed, a door shut). Re-check before walking into it. */
    private boolean stillPossible(ClientLevel level, LocalPlayer player) {
        WorldView live = new WorldView(level);
        boolean safe = safeToCancel(player);
        if (!safe) {
            // Mid-air or committed to a jump: nothing can be done about a change now, so check once back on the ground.
            // The moves ahead stay unverified until then, rather than being marked as checked.
            return true;
        }
        Cell current = tracker.from().cell();
        if (!Moves.isPossible(live, current, tracker.to().cell(), settings)) {
            report = describe(player, "the current move is no longer possible: " + Moves.explain(live, current, tracker.to().cell()));
            PathLog.debug("Rejected move: {}", report);
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
                if (!Moves.isPossible(live, from, next.cell(), settings)) {
                    report = describe(player, "a move ahead is no longer possible: " + Moves.explain(live, from, next.cell()));
                    PathLog.debug("Rejected move: {}", report);
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
        if (type == MoveType.SWIM) {
            steerSwim(level, player, input, a, b);
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
            case LADDER -> {
                input.jump = dy > 0; // jump climbs; letting go slides down
                // Keep the body on the ladder's column: a body that drifts out of the block lets go and falls.
                if (horizontal > LADDER_CENTRE_TOLERANCE) {
                    double[] local = Gait.local(player.getYRot(), dx, dz);
                    double amount = Math.min(LADDER_CENTRE_PUSH, horizontal);
                    input.drive(local[0] * amount, local[1] * amount);
                }
                setSprint(player, input, false);
            }
            case PARKOUR -> steerJump(player, input, a, b, false);
            case RUN_JUMP -> steerJump(player, input, a, b, true);
            case WALK, DIAGONAL, CLIMB, DROP, NEO, SIDE_JUMP, SWIM -> { }
        }
        return true;
    }

    /**
     * Swimming. Steered like walking: the feet follow a carrot along the route (so a run of water cells is one smooth
     * swim, not a hop from centre to centre), the view eases toward it, and the body is driven by stick values so it
     * keeps going the right way while the view turns. Height is held separately: jump rises (and keeps the head above the
     * surface for breath), sneak sinks, and the view pitches toward the target so a sprint-swim heads that way too.
     */
    private void steerSwim(ClientLevel level, LocalPlayer player, PathingInput input, Cell a, Cell b) {
        double px = player.getX();
        double pz = player.getZ();
        double[][] line = routeLine(level, player, a, b, MoveType.SWIM);
        Carrot.Point feetPoint = Carrot.along(line[0], line[1], line[2], stops(line), FEET_CARROT);
        double facing = player.getYRot();
        double heading = steadyHeading(feetPoint, px, pz, facing);

        double dy = b.y() + 0.5 - player.getY();
        double horizontal = Math.hypot(b.x() + 0.5 - px, b.z() + 0.5 - pz);
        double pitch = Math.max(-SWIM_PITCH_LIMIT, Math.min(SWIM_PITCH_LIMIT, -Math.toDegrees(Math.atan2(dy, Math.max(horizontal, 1)))));
        double[] view = look.step(facing, player.getXRot(), heading, pitch);
        player.setYRot((float) view[0]);
        player.setXRot((float) view[1]);

        double toCarrot = Math.hypot(feetPoint.x() - px, feetPoint.z() - pz);
        if (toCarrot > SWIM_HOLD_DISTANCE) {
            Gait.Drive drive = Gait.drive(view[0], heading, 1);
            input.drive(drive.forward(), drive.left());
            setSprint(player, input, settings.allowSprint() && drive.sprintOk());
        } else {
            setSprint(player, input, false); // straight up or down: no need to push sideways
        }
        input.jump = dy > -0.2; // holding jump keeps the head at the surface, letting go sinks
        input.sneak = dy < -0.4;
        updateHopping(false);
        holdSprint(false);
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
        double heading = steadyHeading(feetPoint, px, pz, facing);

        double[] eyes = eyeAim(px, pz, heading, b, eyePoint);
        double[] view = look.step(facing, player.getXRot(), eyes[0], eyes[1]);
        player.setYRot((float) view[0]);
        player.setXRot((float) view[1]);

        double wanted = wantedSpeed(a, b, px, pz);
        boolean brake = false;
        if (feetPoint.stoppedAtCorner()) {
            double fraction = brakeFraction(feetPoint.travelled());
            brake = fraction < wanted;
            wanted = Math.min(wanted, fraction);
        }
        if (brake != braking) {
            braking = brake;
            PathLog.debug("Braking {} {} blocks before a stop at {}x speed (asking for {} of full)", brake ? "starts" : "ends",
                    String.format("%.2f", feetPoint.travelled()), String.format("%.2f", settings.profile().factor()),
                    String.format("%.2f", wanted));
        }
        if (type == MoveType.DROP) {
            wanted = Math.min(wanted, dropSpeed(player, b));
        }
        speed += Math.max(-SPEED_DOWN, Math.min(SPEED_UP, wanted - speed));
        Gait.Drive drive = Gait.drive(view[0], heading, speed);
        input.drive(drive.forward(), drive.left());

        double toCentreX = b.x() + 0.5 - px;
        double toCentreZ = b.z() + 0.5 - pz;
        switch (type) {
            case WALK, DIAGONAL -> {
                // On a ledge a block lower than the target (knocked down a step): jump back up.
                input.jump = player.onGround() && b.y() > player.getY() + 0.5;
                setSprint(player, input, sprintDecision(view[0], heading));
                boolean hop = shouldHop(level, player, a, b, view[0], heading);
                if (hop) {
                    input.jump = true;
                }
                updateHopping(hop || (hopping && !player.onGround()));
            }
            case CLIMB -> {
                // Out of water there is no ground to jump from: holding jump swims up, and pushing into the bank with the
                // head clear gives vanilla's climb-out boost. On a ladder, jump keeps climbing to the exit.
                input.jump = (player.onGround() && Math.hypot(toCentreX, toCentreZ) <= CLIMB_JUMP_DISTANCE)
                        || player.onClimbable() || player.isInWater();
                updateHopping(false);
                holdSprint(false);
                setSprint(player, input, false);
            }
            default -> {
                if (!player.onGround() && !player.isInWater() && !player.onClimbable()) {
                    steerFall(input, player, b, view[0]);
                }
                updateHopping(false);
                holdSprint(false);
                setSprint(player, input, false);
            }
        }
    }

    /**
     * The feet's heading toward their carrot, held steady once the carrot is very close. Right on top of a stop point the
     * direction to it swings round with every centimetre of movement, and following that makes the view shake as the
     * walk ends.
     */
    private double steadyHeading(Carrot.Point feetPoint, double px, double pz, double facing) {
        double dx = feetPoint.x() - px;
        double dz = feetPoint.z() - pz;
        if (Math.hypot(dx, dz) > HEADING_HOLD_DISTANCE || Double.isNaN(lastHeading)) {
            lastHeading = Math.hypot(dx, dz) > 0.05 ? Gait.yawTo(dx, dz) : facing;
        }
        return lastHeading;
    }

    /**
     * How fast to walk off an edge, as a share of full speed. Horizontal speed is kept through the fall (air drag only
     * takes 9% a tick), so the body lands about 4 to 6 times its speed per tick beyond where it tipped off. Taking a
     * drop at a walk lands it a block too far. When the route carries straight on at the landing height that is
     * harmless and the drop is taken at speed. Otherwise the edge is walked off slowly enough to land in the target
     * cell, and {@link #steerFall} trims the rest in the air.
     */
    private double dropSpeed(LocalPlayer player, Cell b) {
        Waypoint next = tracker.ahead(0);
        Cell a = tracker.from().cell();
        boolean straightOn = next != null && isWalkOrDiagonal(next.type()) && next.cell().y() == b.y()
                && next.cell().x() - b.x() == b.x() - a.x() && next.cell().z() - b.z() == b.z() - a.z();
        if (straightOn) {
            return DROP_SPEED;
        }
        double fullWalk = SpeedProfile.BASE_WALK * settings.profile().factor();
        return Math.min(DROP_SPEED, FallPredictor.EDGE_SPEED / fullWalk);
    }

    /**
     * In the air during a drop: predicts where the body will come down from its speed and the height still to fall, and
     * pushes against the error with the little air control vanilla allows, so it lands on the target cell's centre.
     */
    private void steerFall(PathingInput pathingInput, LocalPlayer player, Cell b, double viewYaw) {
        Vec3 velocity = player.getDeltaMovement();
        double drift = FallPredictor.carry(player.getY(), velocity.y, b.y());
        double errorX = b.x() + 0.5 - (player.getX() + velocity.x * drift);
        double errorZ = b.z() + 0.5 - (player.getZ() + velocity.z * drift);
        double error = Math.hypot(errorX, errorZ);
        if (error < FALL_TOLERANCE) {
            pathingInput.drive(0, 0);
            return;
        }
        double[] local = Gait.local(viewYaw, errorX, errorZ);
        double amount = Math.min(1, error / FALL_FULL_CORRECTION);
        pathingInput.drive(local[0] * amount, local[1] * amount);
    }

    private void updateHopping(boolean now) {
        if (now != hopping) {
            hopping = now;
            PathLog.debug("Hopping {} at {} (hop {} t/block vs sprint {} t/block)", now ? "starts" : "stops",
                    cellText(tracker.to().cell()), String.format("%.2f", settings.profile().hop()),
                    String.format("%.2f", settings.profile().sprint()));
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

    /**
     * Sprint-jumping along a straight, level, open stretch, when that is faster than sprinting for this player. Jump the
     * moment the body is back on the ground, so the sprint boost is never wasted. Only while sprinting already and lined
     * up with the heading, and only with a clear straight run ahead, since a hop carries about four blocks and a turn,
     * a step or a low ceiling in them is a collision. Not near the end of a route that stops at the goal.
     */
    private boolean shouldHop(ClientLevel level, LocalPlayer player, Cell a, Cell b, double viewYaw, double heading) {
        if (!settings.profile().hops() || !settings.allowParkour() || !settings.allowSprint()) {
            return false;
        }
        if (!player.onGround() || !player.isSprinting() || speed < 0.95) {
            return false;
        }
        if (Math.abs(LookControl.wrap(heading - viewYaw)) > HOP_ALIGNED_ANGLE || a.y() != b.y()) {
            return false;
        }
        if (slowAtEnd) {
            Cell last = path.last();
            if (Math.hypot(last.x() - player.getX(), last.z() - player.getZ()) < HOP_END_CLEARANCE) {
                return false;
            }
        }
        WorldView world = new WorldView(level);
        int dx = b.x() - a.x();
        int dz = b.z() - a.z();
        if (!Moves.hopRoom(world, a) || !Moves.hopRoom(world, b)) {
            return false;
        }
        Cell previous = b;
        for (int k = 0; k < HOP_STRAIGHT_CELLS; k++) {
            Waypoint next = tracker.ahead(k);
            if (next == null || !isWalkOrDiagonal(next.type())) {
                return false;
            }
            Cell cell = next.cell();
            if (cell.y() != b.y() || cell.x() - previous.x() != dx || cell.z() - previous.z() != dz || !Moves.hopRoom(world, cell)) {
                return false;
            }
            previous = cell;
        }
        return true;
    }

    /**
     * How much of full speed to ask for with this far to go to a spot the body has to stop at, so that the coasting after
     * letting go of the input ends at the spot instead of past it. At vanilla speed this only bites in the last third of a
     * block. At 500% it starts nearly two blocks out, and without it the body would shoot past cells and corners.
     */
    private double brakeFraction(double distance) {
        double fullSpeed = SpeedProfile.BASE_SPRINT * settings.profile().factor();
        return Math.max(BRAKE_FLOOR, Math.min(1, distance / (COAST * fullSpeed)));
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
            } else if (!(isWalkOrDiagonal(out.type()) && isWalkOrDiagonal(into))
                    && !(out.type() == MoveType.SWIM && into == MoveType.SWIM)) {
                stop = true; // the next move needs the body here, or the way of moving changes
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
        boolean swimming = into == MoveType.SWIM && out == MoveType.SWIM;
        boolean walking = into == MoveType.WALK && out == MoveType.WALK;
        if (!(cardinal && level && rightAngle && (walking || swimming))) {
            return false;
        }
        int inX = after.x() - d1x;
        int inZ = after.z() - d1z;
        return open(world.terrainAt(inX, here.y(), inZ), swimming) && open(world.terrainAt(inX, here.y() + 1, inZ), swimming);
    }

    /** A cell the body can pass through when cutting a corner: air, or water too while swimming. */
    private static boolean open(Terrain terrain, boolean swimming) {
        return terrain == Terrain.AIR || (swimming && terrain == Terrain.WATER);
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
        input.drive(settings.jumpThrottle(), 0); // a faster body runs up at vanilla pace, which is what the jump model assumes
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

        input.drive(settings.jumpThrottle(), 0); // a faster body runs up at vanilla pace, which is what the jump model assumes
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
