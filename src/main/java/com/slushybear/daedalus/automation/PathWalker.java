package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.pathfinding.Cell;
import com.slushybear.daedalus.pathfinding.JumpPlanner;
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

    private final Path path;
    private final PathSettings settings;
    private final PathTracker tracker;
    private int verifiedMove = -1;
    private String report = "";

    public PathWalker(Path path, PathSettings settings) {
        this.path = path;
        this.settings = settings;
        this.tracker = new PathTracker(path);
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

        steer(player, input);
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

    private void steer(LocalPlayer player, PathingInput input) {
        Cell a = tracker.from().cell();
        Cell b = tracker.to().cell();
        MoveType type = tracker.to().type();
        boolean floating = type == MoveType.SWIM || type == MoveType.LADDER;

        double dx = b.x() + 0.5 - player.getX();
        double dz = b.z() + 0.5 - player.getZ();
        double dy = b.y() + (floating ? 0.5 : 0) - player.getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // Minecraft yaw: forward is (-sin yaw, cos yaw), so yaw = atan2(-dx, dz). Hold the view when right on top of the target.
        if (horizontal > 0.05) {
            player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        player.setXRot(type == MoveType.SWIM && horizontal > 0.05
                ? (float) Math.max(-60, Math.min(60, -Math.toDegrees(Math.atan2(dy, horizontal))))
                : 0);

        switch (type) {
            case WALK, DIAGONAL -> {
                input.forward = true;
                // On a ledge a block lower than the target (knocked down a step): jump back up.
                input.jump = player.onGround() && b.y() > player.getY() + 0.5;
                setSprint(player, input, sprintableAhead());
            }
            case CLIMB -> {
                input.forward = true;
                input.jump = (player.onGround() && horizontal <= CLIMB_JUMP_DISTANCE) || player.onClimbable();
                setSprint(player, input, false);
            }
            case DROP -> {
                // Walk off, then stop pushing once momentum will carry the body over the landing, so it does not overshoot.
                Vec3 velocity = player.getDeltaMovement();
                double aheadX = b.x() + 0.5 - (player.getX() + velocity.x);
                double aheadZ = b.z() + 0.5 - (player.getZ() + velocity.z);
                input.forward = Math.sqrt(aheadX * aheadX + aheadZ * aheadZ) > 0.15;
                setSprint(player, input, false);
            }
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
            case PARKOUR -> steerJump(player, input, a, b);
        }
    }

    /**
     * A gap jump. Plain jumps for gaps of 1 and 2 blocks land short enough for a narrow landing, so sprinting is only used
     * for a 3-block gap and for jumping up. The moment to press jump comes from {@link JumpPlanner}, which simulates the
     * jump from the real position and speed and jumps at the last moment that still lands.
     */
    private void steerJump(LocalPlayer player, PathingInput input, Cell a, Cell b) {
        int dirX = Integer.signum(b.x() - a.x());
        int dirZ = Integer.signum(b.z() - a.z());
        int cells = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
        int rise = b.y() - a.y();

        input.forward = true;
        setSprint(player, input, cells >= 4 || rise > 0);

        double progress = (player.getX() - (a.x() + 0.5)) * dirX + (player.getZ() - (a.z() + 0.5)) * dirZ;
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
