package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.motion.LookControl;
import com.slushybear.daedalus.pathfinding.Cell;
import com.slushybear.daedalus.pathfinding.Goal;
import com.slushybear.daedalus.pathfinding.GridSnapshot;
import com.slushybear.daedalus.pathfinding.Navigator;
import com.slushybear.daedalus.pathfinding.Path;
import com.slushybear.daedalus.pathfinding.PathSettings;
import com.slushybear.daedalus.pathfinding.StartCell;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * Gets the player to a goal, one segment at a time.
 *
 * <ol>
 *   <li>Copy the terrain around the player (on the client thread) and plan on a worker thread. The plan may end short
 *   of the goal when the goal is outside the copied area or the time runs out.</li>
 *   <li>Walk it with a {@link PathWalker}, which drives the player's input directly. A few moves before a segment ends,
 *   the next one is already being planned from where it ends, so the walk carries straight on instead of pausing.</li>
 *   <li>When the walker reports a problem, wait until stopping is safe (never mid-jump), then plan again from where the
 *   player stands, favouring the old route and avoiding the move that failed.</li>
 * </ol>
 *
 * <p>Pathing decides how to get there. Whether to keep going at all is the caller's decision, so cancel() is always safe.
 * Everything worth knowing about a walk goes to {@link PathLog}.
 */
public final class NavigationTask implements Task {
    private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "daedalus-path-planner");
        thread.setDaemon(true);
        return thread;
    });

    /** Start planning the next segment once the current one has this few moves left. */
    private static final int PREPLAN_MOVES = 6;

    /** A finished search, tagged so a result for an older request can never be mistaken for the current one. */
    private record Plan(int generation, Navigator.Outcome outcome, Cell start) {}

    private final Goal goal;
    private final String targetText;
    /** The rules the newest plan was made under. The walker checks moves against the same ones. */
    private PathSettings settings = PathSettings.defaults();
    private final InputControl control = new InputControl();
    /** Cells of moves that failed, kept out of later plans. Cleared whenever the walk makes progress. */
    private final Set<Cell> excluded = new HashSet<>();
    private final LookControl look = new LookControl();

    private int generation;
    private int failuresInARow;
    private int elapsed;
    private int waited;
    private int segments;
    private Future<Plan> pending;
    /** Where the pending plan starts if it was started early, from the end of the segment being walked; else null. */
    private Cell pendingFromSegmentEnd;
    private PathWalker walk;
    private boolean walkReachesGoal;
    private Path lastPath;
    private String failure;

    /** Call from the client thread. */
    public NavigationTask(Minecraft client, Goal goal, String targetText) {
        this.goal = goal;
        this.targetText = targetText;
        PathLog.info("Navigation to {} started", targetText);
        startPlan(client);
    }

    @Override
    public boolean tick(Minecraft client) {
        if (failure != null) {
            return true;
        }
        if (client.player == null || client.level == null) {
            return fail(client, "lost the player");
        }
        if (++elapsed > Limits.GOTO_TIMEOUT_TICKS) {
            return fail(client, "timed out");
        }
        try {
            return walk != null ? walkTick(client) : planTick(client);
        } catch (RuntimeException e) {
            PathLog.error("Navigation to {} crashed", e, targetText);
            return fail(client, "an error in the pathfinder (see logs/daedalus-pathfinder.log)");
        }
    }

    private boolean walkTick(Minecraft client) {
        PathingInput input = control.take(client);
        PathWalker.Status status = walk.step(client, input);
        PathDisplay.show(walk.path(), walk.moveIndex());
        switch (status) {
            case RUNNING -> {
                if (!walkReachesGoal && pending == null && walk.movesLeft() <= PREPLAN_MOVES) {
                    // Plan the next segment from where this one ends, while still walking it.
                    startPlan(client, walk.path().last(), walk.path());
                }
                return false;
            }
            case ARRIVED -> {
                Path walked = walk.path();
                lastPath = walked;
                walk = null;
                input.release();
                if (walkReachesGoal || goal.isEnd(startCell(client))) {
                    finish(client);
                    PathLog.info("Navigation to {} arrived after {} segment(s), {} ticks", targetText, segments + 1, elapsed);
                    return true;
                }
                // A segment toward a far goal finished. That is progress: forget old failures.
                failuresInARow = 0;
                excluded.clear();
                segments++;
                boolean early = pending != null && walked.last().equals(pendingFromSegmentEnd);
                PathLog.debug("Segment {} done at {}; next plan {}", segments, cellText(walked.last()),
                        early ? (pending.isDone() ? "is ready" : "is still running") : "starts now");
                if (!early) {
                    startPlan(client);
                }
                return planTick(client);
            }
            default -> {
                if (!walk.safeToCancel(client.player)) {
                    return false; // mid-jump or mid-fall: let it land before changing plans
                }
                Cell failed = walk.target();
                if (status != PathWalker.Status.OFF_PATH && failed != null && !goal.isEnd(failed)) {
                    excluded.add(failed); // the move that failed, so the next plan tries another way
                }
                return replan(client, status.name().toLowerCase().replace('_', ' ') + " (" + walk.report() + ")");
            }
        }
    }

    private boolean planTick(Minecraft client) {
        if (pending == null) {
            return fail(client, "no path search is running");
        }
        if (!pending.isDone()) {
            if (++waited > Limits.PLAN_TIMEOUT_TICKS) {
                return fail(client, "path search took too long");
            }
            return false;
        }

        Plan plan;
        try {
            plan = pending.get();
        } catch (ExecutionException | CancellationException e) {
            PathLog.error("Path search to {} failed", e, targetText);
            return fail(client, "path search failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(client, "path search was interrupted");
        }
        pending = null;
        pendingFromSegmentEnd = null;

        if (plan.generation() != generation) {
            startPlan(client); // defensive: only the newest request may drive the walk
            return failure != null;
        }
        Navigator.Outcome outcome = plan.outcome();
        if (outcome.plan().isEmpty()) {
            if (goal.isEnd(startCell(client))) {
                finish(client);
                PathLog.info("Navigation to {}: already there", targetText);
                return true;
            }
            PathLog.warn("No route to {} from {} ({} cells searched in {} ms, {} cells excluded)", targetText,
                    cellText(plan.start()), outcome.expanded(), outcome.millis(), excluded.size());
            // Cells excluded after failed moves may be what cuts the way off. Forget them and try once more from here.
            if (!excluded.isEmpty() && ++failuresInARow <= Limits.MAX_REPLANS) {
                excluded.clear();
                lastPath = null;
                PathLog.info("Navigation to {}: retrying with no cells excluded ({}/{})", targetText, failuresInARow, Limits.MAX_REPLANS);
                startPlan(client);
                return failure != null;
            }
            return fail(client, "no walkable path to " + targetText);
        }

        Navigator.Plan route = outcome.plan().get();
        walk = new PathWalker(route.path(), settings, look, route.reachesGoal());
        walkReachesGoal = route.reachesGoal();
        PathLog.info("Navigation to {} at {}x speed{}: {} {} waypoints, {} ticks ({} cells searched in {} ms)", targetText,
                String.format("%.2f", settings.profile().factor()), settings.profile().hops() ? " (hopping)" : "",
                route.reachesGoal() ? "route of" : "segment of", route.path().size(), Math.round(route.path().cost()),
                outcome.expanded(), outcome.millis());
        PathLog.debug("Route: {}", summary(route.path()));
        return walkTick(client);
    }

    /** Drops the current walk and plans again from where the player is now. Fails once too many go wrong in a row. */
    private boolean replan(Minecraft client, String reason) {
        lastPath = walk.path();
        walk = null;
        PathDisplay.clear();
        control.giveBack(client);
        if (++failuresInARow > Limits.MAX_REPLANS) {
            return fail(client, reason + ", and ran out of retries");
        }
        PathLog.warn("Navigation to {} {}; replanning ({}/{})", targetText, reason, failuresInARow, Limits.MAX_REPLANS);
        startPlan(client);
        return failure != null;
    }

    /** Plans from where the player is now. */
    private void startPlan(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            failure = "not in a world";
            return;
        }
        Cell start = StartCell.choose(new WorldView(level), player.getX(), player.getY(), player.getZ(),
                player.onGround() || player.isInWater() || player.onClimbable());
        submit(client, start, lastPath);
        pendingFromSegmentEnd = null;
    }

    /** Plans from a cell the player has not reached yet: the end of the segment being walked. */
    private void startPlan(Minecraft client, Cell segmentEnd, Path walking) {
        submit(client, segmentEnd, walking);
        pendingFromSegmentEnd = segmentEnd;
    }

    /** Copies the terrain around `start` on the client thread, then hands the copy to the worker. */
    private void submit(Minecraft client, Cell start, Path previous) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            failure = "not in a world";
            return;
        }
        PathSettings planSettings = PathSettings.defaults().withSpeed(PlayerSpeed.factor(player));
        if (planSettings.profile().factor() != settings.profile().factor()) {
            PathLog.info("Speed changed to {}x: walking {} t/block, sprinting {} t/block, {}",
                    String.format("%.2f", planSettings.profile().factor()), String.format("%.2f", planSettings.profile().walk()),
                    String.format("%.2f", planSettings.profile().sprint()),
                    planSettings.profile().hops() ? String.format("hopping %.2f t/block", planSettings.profile().hop()) : "no hopping");
        }
        settings = planSettings;
        WorldView view = new WorldView(level);
        GridSnapshot snapshot = GridSnapshot.capture(view,
                start.x() - Limits.SEGMENT_RADIUS, start.y() - Limits.SEGMENT_HEIGHT, start.z() - Limits.SEGMENT_RADIUS,
                start.x() + Limits.SEGMENT_RADIUS, start.y() + Limits.SEGMENT_HEIGHT, start.z() + Limits.SEGMENT_RADIUS);

        if (pending != null) {
            pending.cancel(true);
        }
        int id = ++generation;
        Set<Cell> blocked = Set.copyOf(excluded);
        PathLog.debug("Planning from {} toward {} ({} cells excluded)", cellText(start), targetText, blocked.size());
        pending = PLANNER.submit(() -> new Plan(id, Navigator.search(snapshot, start, goal, planSettings, blocked, previous), start));
        waited = 0;
    }

    private static Cell startCell(Minecraft client) {
        LocalPlayer player = client.player;
        return StartCell.feetCell(player.getX(), player.getY(), player.getZ());
    }

    private static String cellText(Cell cell) {
        return cell.x() + " " + cell.y() + " " + cell.z();
    }

    /** The waypoints as "x y z TYPE", for the log, so a failed walk can be matched against the route it was given. */
    private static String summary(Path path) {
        return path.waypoints().stream()
                .map(w -> w.cell().x() + " " + w.cell().y() + " " + w.cell().z() + " " + w.type())
                .collect(Collectors.joining(" > "));
    }

    private void finish(Minecraft client) {
        PathDisplay.clear();
        control.giveBack(client);
    }

    @Override
    public void cancel(Minecraft client) {
        if (pending != null) {
            pending.cancel(true);
        }
        finish(client);
    }

    @Override
    public boolean failed() {
        return failure != null;
    }

    @Override
    public String failure() {
        return failure == null ? "" : failure;
    }

    private boolean fail(Minecraft client, String reason) {
        failure = reason;
        PathLog.warn("Navigation to {} failed: {}", targetText, reason);
        cancel(client);
        return true;
    }
}
