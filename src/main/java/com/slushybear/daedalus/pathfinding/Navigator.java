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

    /** A search and what it cost: the plan if there is one, how many cells it expanded, and how long it took. */
    public record Outcome(Optional<Plan> plan, int expanded, long millis) {}

    private Navigator() {}

    /**
     * @param previous the route being replaced, or null. Its cells are cheaper, so a replan sticks to it where it can
     */
    public static Optional<Plan> plan(BlockView world, Cell start, Goal goal, PathSettings settings, Set<Cell> excluded, Path previous) {
        return search(world, start, goal, settings, excluded, previous).plan();
    }

    /** Like {@link #plan}, with the search's statistics, for the log. */
    public static Outcome search(BlockView world, Cell start, Goal goal, PathSettings settings, Set<Cell> excluded, Path previous) {
        long began = System.nanoTime();
        Set<Cell> favored = previous == null ? Set.of() : new HashSet<>(previous.cells());
        PathSearch.Result result = PathSearch.search(world, start, goal, settings, excluded, favored);
        long millis = (System.nanoTime() - began) / 1_000_000;
        Optional<Plan> plan = result.path() == null
                ? Optional.empty()
                : Optional.of(new Plan(result.path(), result.reachedGoal()));
        return new Outcome(plan, result.expanded(), millis);
    }
}
