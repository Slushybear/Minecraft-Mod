package com.slushybear.daedalus.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* over standing cells using {@link Moves}, with costs in ticks. The search state is a cell plus how long the body
 * has been underwater, so a route cannot swim further than the breath limit.
 *
 * <p>It stops on whichever comes first: the goal, the node cap, or a time limit. When it stops short of the goal it still
 * returns a useful partial route if one exists, so a far goal is reached one segment at a time.
 */
public final class PathSearch {
    /**
     * For a partial route, the candidates are ranked by remaining estimate plus cost divided by each of these. A small
     * divisor trusts cost, a large one trusts closeness to the goal. The first that gets at least 5 blocks from the
     * start wins, so a cheap short hop is preferred over a long detour that only looks close.
     */
    private static final double[] PARTIAL_WEIGHTS = {1.5, 2, 2.5, 3, 4, 5, 10};
    private static final double MIN_PARTIAL_DISTANCE_SQ = 5 * 5;
    /** Ignore a cheaper route to a cell unless it saves this many ticks. Re-queueing for less is wasted work. */
    private static final double MIN_IMPROVEMENT = 0.01;
    /** Moves into cells of the previous route cost this fraction, so a replan prefers to stay on the old route. */
    public static final double FAVOR = 0.5;
    /** Reading the clock is not free, so check it every this many nodes. */
    private static final int CLOCK_INTERVAL = 64;

    /**
     * @param path the route found, or null if there is nothing worth walking
     * @param reachedGoal true if the route ends in the goal, false if it is a partial segment toward it
     */
    public record Result(Path path, boolean reachedGoal, int expanded) {}

    private PathSearch() {}

    public static Result search(BlockView world, Cell start, Goal goal, PathSettings settings, Set<Cell> excluded, Set<Cell> favored) {
        // The heuristic needs the cheapest flat cost for this player, or it would overestimate for a fast one.
        Costs.useMinFlat(settings.fastestFlat());
        try {
            return run(world, start, goal, settings, excluded, favored);
        } finally {
            Costs.resetMinFlat();
        }
    }

    private static Result run(BlockView world, Cell start, Goal goal, PathSettings settings, Set<Cell> excluded, Set<Cell> favored) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<State, Node> best = new HashMap<>();

        State first = new State(start, 0);
        Node root = new Node(first, null, MoveType.WALK, 0, 0, goal.heuristic(start));
        open.add(root);
        best.put(first, root);

        Node[] partial = new Node[PARTIAL_WEIGHTS.length];
        double[] partialScore = new double[PARTIAL_WEIGHTS.length];
        for (int i = 0; i < partial.length; i++) {
            partial[i] = root;
            partialScore[i] = root.f();
        }
        boolean failing = true;

        long startTime = System.currentTimeMillis();
        int expanded = 0;
        while (!open.isEmpty()) {
            if (expanded % CLOCK_INTERVAL == 0) {
                if (Thread.currentThread().isInterrupted()) {
                    return new Result(null, false, expanded); // the caller gave up, for example a cancelled macro
                }
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= settings.failureTimeoutMs() || (!failing && elapsed >= settings.primaryTimeoutMs())) {
                    break;
                }
            }
            Node current = open.poll();
            if (current != best.get(current.state())) {
                continue; // a cheaper route to this state was found after this entry was queued
            }
            Cell cell = current.state().cell();
            if (goal.isEnd(cell)) {
                return new Result(rebuild(current), true, expanded);
            }
            if (++expanded > settings.maxNodes()) {
                break;
            }

            for (Moves.Move move : Moves.from(world, cell, current.state().submerged(), settings)) {
                if (excluded.contains(move.to())) {
                    continue;
                }
                double cost = favored.contains(move.to()) ? move.cost() * FAVOR : move.cost();
                State state = new State(move.to(), move.submerged());
                double g = current.g() + cost;
                Node existing = best.get(state);
                if (existing != null && existing.g() - g <= MIN_IMPROVEMENT) {
                    continue;
                }
                double h = goal.heuristic(move.to());
                Node next = new Node(state, current, move.type(), move.cost(), g, g + h);
                best.put(state, next);
                open.add(next);

                for (int i = 0; i < PARTIAL_WEIGHTS.length; i++) {
                    double score = h + g / PARTIAL_WEIGHTS[i];
                    if (partialScore[i] - score > MIN_IMPROVEMENT) {
                        partialScore[i] = score;
                        partial[i] = next;
                        if (failing && distanceSq(next, start) > MIN_PARTIAL_DISTANCE_SQ) {
                            failing = false;
                        }
                    }
                }
            }
        }

        for (Node candidate : partial) {
            if (distanceSq(candidate, start) > MIN_PARTIAL_DISTANCE_SQ) {
                return new Result(rebuild(candidate), false, expanded);
            }
        }
        return new Result(null, false, expanded);
    }

    /** A complete route to the goal with default settings and no time limits, or empty. Mostly for tests. */
    public static Optional<Path> findPath(BlockView world, Cell start, Goal goal, int maxNodes, Set<Cell> excluded) {
        return findPath(world, start, goal, PathSettings.defaults().withMaxNodes(maxNodes), excluded);
    }

    /** A complete route to the goal under the given settings, with no time limits, or empty. */
    public static Optional<Path> findPath(BlockView world, Cell start, Goal goal, PathSettings settings, Set<Cell> excluded) {
        Result result = search(world, start, goal, settings.withoutTimeLimits(), excluded, Set.of());
        return result.reachedGoal() ? Optional.of(result.path()) : Optional.empty();
    }

    /** Same as {@link #findPath(BlockView, Cell, Goal, int, Set)}, returning just the cells. */
    public static Optional<List<Cell>> find(BlockView world, Cell start, Goal goal, int maxNodes, Set<Cell> excluded) {
        return findPath(world, start, goal, maxNodes, excluded).map(Path::cells);
    }

    /** Shorthand for a GoalBlock search with nothing excluded, returning just the cells. */
    public static Optional<List<Cell>> find(BlockView world, Cell start, Cell target, int tolerance, int maxNodes) {
        return find(world, start, new GoalBlock(target, tolerance), maxNodes, Set.of());
    }

    private static double distanceSq(Node node, Cell start) {
        Cell cell = node.state().cell();
        double dx = cell.x() - start.x();
        double dy = cell.y() - start.y();
        double dz = cell.z() - start.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Path rebuild(Node last) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (Node node = last; node != null; node = node.parent()) {
            waypoints.add(new Waypoint(node.state().cell(), node.type(), node.moveCost()));
        }
        Collections.reverse(waypoints);
        return new Path(waypoints);
    }

    private record State(Cell cell, int submerged) {}

    /** moveCost is the real expected ticks of the move into this node, before any favouring discount. */
    private record Node(State state, Node parent, MoveType type, double moveCost, double g, double f) {}
}
