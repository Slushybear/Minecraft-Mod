package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.DaedalusClient;
import com.slushybear.daedalus.pathfinding.BlockView;
import com.slushybear.daedalus.pathfinding.Cell;
import com.slushybear.daedalus.pathfinding.PathSearch;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;

/**
 * Plans a path on a worker thread, then walks it. The worker only reads a copied snapshot, never the live world.
 * While the search runs the macro just waits, so the game keeps its frame rate.
 */
public final class PathPlanTask implements Task {
    private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "daedalus-path-planner");
        thread.setDaemon(true);
        return thread;
    });

    private final Future<Optional<List<Cell>>> plan;
    private final String targetText;
    private Task walk;
    private int waited;
    private String failure;

    /** snapshot must be an immutable copy, such as a GridSnapshot. Call this from the client thread. */
    public PathPlanTask(BlockView snapshot, Cell start, Cell goal, String targetText) {
        this.targetText = targetText;
        this.plan = PLANNER.submit(() -> PathSearch.find(snapshot, start, goal, 1, Limits.MAX_PATH_NODES));
    }

    @Override
    public boolean tick(Minecraft client) {
        if (walk != null) {
            return walk.tick(client);
        }
        if (!plan.isDone()) {
            if (++waited > Limits.PLAN_TIMEOUT_TICKS) {
                plan.cancel(true);
                failure = "path search took too long";
                return true;
            }
            return false;
        }

        try {
            Optional<List<Cell>> result = plan.get();
            if (result.isEmpty()) {
                failure = "no walkable path to " + targetText;
                return true;
            }
            walk = new FollowPathTask(result.get());
            return walk.tick(client);
        } catch (ExecutionException | CancellationException e) {
            DaedalusClient.LOGGER.warn("Path search failed", e);
            failure = "path search failed";
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure = "path search was interrupted";
            return true;
        }
    }

    @Override
    public void cancel(Minecraft client) {
        plan.cancel(true);
        if (walk != null) {
            walk.cancel(client);
        }
    }

    @Override
    public boolean failed() {
        return failure != null || (walk != null && walk.failed());
    }

    @Override
    public String failure() {
        if (failure != null) {
            return failure;
        }
        return walk == null ? "" : walk.failure();
    }
}
