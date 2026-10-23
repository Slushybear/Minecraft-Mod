package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.DaedalusClient;
import com.slushybear.daedalus.pathfinding.Cell;
import com.slushybear.daedalus.pathfinding.Goal;
import com.slushybear.daedalus.pathfinding.GridSnapshot;
import com.slushybear.daedalus.pathfinding.Navigator;
import com.slushybear.daedalus.pathfinding.Path;
import com.slushybear.daedalus.pathfinding.PathSettings;
import com.slushybear.daedalus.pathfinding.StartCell;
import java.util.HashSet;
import java.util.Optional;
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
 *   <li>Walk it with a {@link PathWalker}, which drives the player's input directly.</li>
 *   <li>When a segment ends short of the goal, plan the next one from there. When the walker reports a problem, wait until
 *   stopping is safe (never mid-jump), then plan again from where the player stands, favouring the old route.</li>
 * </ol>
 *
 * <p>Pathing decides how to get there. Whether to keep going at all is the caller's decision, so cancel() is always safe.
 */
public final class NavigationTask implements Task {
    private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "daedalus-path-planner");
        thread.setDaemon(true);
        return thread;
    });

    /** A finished plan, tagged so a result for an older request can never be mistaken for the current one. */
    private record Plan(int generation, Optional<Navigator.Plan> route) {}

    private final Goal goal;
    private final String targetText;
    private final PathSettings settings = PathSettings.defaults();
    private final InputControl control = new InputControl();
    private final Set<Cell> excluded = new HashSet<>();

    private int generation;
    private int failuresInARow;
    private int elapsed;
    private int waited;
    private int segments;
    private Future<Plan> pending;
    private PathWalker walk;
    private boolean walkReachesGoal;
    private Path lastPath;
    private String failure;

    /** Call from the client thread. */
    public NavigationTask(Minecraft client, Goal goal, String targetText) {
        this.goal = goal;
        this.targetText = targetText;
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
        return walk != null ? walkTick(client) : planTick(client);
    }

    private boolean walkTick(Minecraft client) {
        PathingInput input = control.take(client);
        PathWalker.Status status = walk.step(client, input);
        switch (status) {
            case RUNNING -> {
                return false;
            }
            case ARRIVED -> {
                lastPath = walk.path();
                walk = null;
                input.release();
                if (walkReachesGoal || goal.isEnd(startCell(client))) {
                    control.giveBack(client);
                    DaedalusClient.LOGGER.info("Navigation to {} arrived after {} segment(s)", targetText, segments + 1);
                    return true;
                }
                // A segment toward a far goal finished. That is progress, so reset the failure count.
                failuresInARow = 0;
                segments++;
                startPlan(client);
                return failure != null;
            }
            default -> {
                if (!walk.safeToCancel(client.player)) {
                    return false; // mid-jump or mid-fall: let it land before changing plans
                }
                if (status != PathWalker.Status.OFF_PATH && walk.target() != null) {
                    excluded.add(walk.target()); // the move that failed, so the next plan tries another way
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
            DaedalusClient.LOGGER.warn("Path search failed", e);
            return fail(client, "path search failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(client, "path search was interrupted");
        }
        pending = null;

        if (plan.generation() != generation) {
            startPlan(client); // defensive: only the newest request may drive the walk
            return failure != null;
        }
        if (plan.route().isEmpty()) {
            if (goal.isEnd(startCell(client))) {
                control.giveBack(client);
                return true; // already there
            }
            return fail(client, "no walkable path to " + targetText);
        }

        Navigator.Plan route = plan.route().get();
        walk = new PathWalker(route.path(), settings);
        walkReachesGoal = route.reachesGoal();
        DaedalusClient.LOGGER.info("Navigation to {}: {} {} waypoints, {} ticks: {}", targetText,
                route.reachesGoal() ? "route of" : "segment of", route.path().size(), Math.round(route.path().cost()),
                summary(route.path()));
        return walkTick(client);
    }

    /** Drops the current walk and plans again from where the player is now. Fails once too many go wrong in a row. */
    private boolean replan(Minecraft client, String reason) {
        lastPath = walk.path();
        walk = null;
        control.giveBack(client);
        if (++failuresInARow > Limits.MAX_REPLANS) {
            return fail(client, reason + ", and ran out of retries");
        }
        DaedalusClient.LOGGER.info("Navigation to {} {}, replanning ({}/{})", targetText, reason, failuresInARow, Limits.MAX_REPLANS);
        startPlan(client);
        return failure != null;
    }

    /** Copies the terrain around the player on the client thread, then hands the copy to the worker. */
    private void startPlan(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            failure = "not in a world";
            return;
        }

        WorldView view = new WorldView(level);
        Cell start = StartCell.choose(view, player.getX(), player.getY(), player.getZ(),
                player.onGround() || player.isInWater() || player.onClimbable());
        GridSnapshot snapshot = GridSnapshot.capture(view,
                start.x() - Limits.SEGMENT_RADIUS, start.y() - Limits.SEGMENT_HEIGHT, start.z() - Limits.SEGMENT_RADIUS,
                start.x() + Limits.SEGMENT_RADIUS, start.y() + Limits.SEGMENT_HEIGHT, start.z() + Limits.SEGMENT_RADIUS);

        if (pending != null) {
            pending.cancel(true);
        }
        int id = ++generation;
        Set<Cell> blocked = Set.copyOf(excluded);
        Path previous = lastPath;
        pending = PLANNER.submit(() -> new Plan(id, Navigator.plan(snapshot, start, goal, settings, blocked, previous)));
        waited = 0;
    }

    private static Cell startCell(Minecraft client) {
        LocalPlayer player = client.player;
        return StartCell.feetCell(player.getX(), player.getY(), player.getZ());
    }

    /** The waypoints as "x y z TYPE", for the log, so a failed walk can be matched against the route it was given. */
    private static String summary(Path path) {
        return path.waypoints().stream()
                .map(w -> w.cell().x() + " " + w.cell().y() + " " + w.cell().z() + " " + w.type())
                .collect(Collectors.joining(" > "));
    }

    @Override
    public void cancel(Minecraft client) {
        if (pending != null) {
            pending.cancel(true);
        }
        control.giveBack(client);
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
        cancel(client);
        return true;
    }
}
