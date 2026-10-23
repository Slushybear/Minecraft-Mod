package com.slushybear.daedalus.pathfinding;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Plans the next stretch of a walk. Everything here is pure, so it runs on a worker thread over a terrain snapshot.
 * A plan may stop short of the goal, in which case the walker plans again from wherever that stretch ends.
 */
public final class Navigator {
    /**
     * @param path the route to walk
     * @param reachesGoal true if it ends in the goal, false if it is one segment toward it
     */
    public record Plan(Path path, boolean reachesGoal) {}

    private Navigator() {}

    /**
     * @param previous the route being replaced, or null. Its cells are cheaper, so a replan sticks to it where it can
     */
    public static Optional<Plan> plan(BlockView world, Cell start, Goal goal, PathSettings settings, Set<Cell> excluded, Path previous) {
        Set<Cell> favored = previous == null ? Set.of() : new HashSet<>(previous.cells());
        PathSearch.Result result = PathSearch.search(world, start, goal, settings, excluded, favored);
        if (result.path() == null) {
            return Optional.empty();
        }
        return Optional.of(new Plan(result.path(), result.reachedGoal()));
    }
}
