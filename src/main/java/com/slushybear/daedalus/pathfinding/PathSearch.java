package com.slushybear.daedalus.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * A* over standing cells. Moves: step sideways, climb one block, or drop 1 to 3 blocks.
 * Every move needs a solid floor, and the two-block-high body has to fit.
 * Pure logic with no Minecraft types.
 */
public final class PathSearch {
    private static final int WALK_COST = 10;
    private static final int CLIMB_COST = 15;
    private static final int DROP_BASE_COST = 10;
    private static final int DROP_COST_PER_BLOCK = 3;
    /** Lower bound on the cost of each block of height, so the heuristic never overestimates. */
    private static final int HEIGHT_COST = 3;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[] VERTICAL_MOVES = {0, 1, -1, -2, -3};

    private PathSearch() {}

    /**
     * Finds a path from start to a cell within `tolerance` blocks of goal (height within 1).
     * Gives up after maxNodes expansions. Returns empty if there is no path or the limit was hit.
     */
    public static Optional<List<Cell>> find(BlockView world, Cell start, Cell goal, int tolerance, int maxNodes) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::f));
        Map<Cell, Node> best = new HashMap<>();

        Node first = new Node(start, null, 0, heuristic(start, goal));
        open.add(first);
        best.put(start, first);

        int expanded = 0;
        while (!open.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                return Optional.empty(); // the caller gave up, for example a cancelled macro
            }
            Node current = open.poll();
            if (current != best.get(current.cell())) {
                continue; // a cheaper route to this cell was found after this entry was queued
            }
            if (isGoal(current.cell(), goal, tolerance)) {
                return Optional.of(rebuild(current));
            }
            if (++expanded > maxNodes) {
                return Optional.empty();
            }

            Cell from = current.cell();
            for (int[] side : SIDES) {
                for (int dy : VERTICAL_MOVES) {
                    Cell to = new Cell(from.x() + side[0], from.y() + dy, from.z() + side[1]);
                    if (!canStand(world, from, to, dy)) {
                        continue;
                    }
                    int g = current.g() + moveCost(dy);
                    Node existing = best.get(to);
                    if (existing != null && existing.g() <= g) {
                        continue;
                    }
                    Node next = new Node(to, current, g, g + heuristic(to, goal));
                    best.put(to, next);
                    open.add(next);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean canStand(BlockView world, Cell from, Cell to, int dy) {
        if (world.terrainAt(to.x(), to.y() - 1, to.z()) != Terrain.SOLID) {
            return false; // no floor
        }
        if (world.terrainAt(to.x(), to.y(), to.z()) != Terrain.AIR) {
            return false; // feet blocked
        }
        if (world.terrainAt(to.x(), to.y() + 1, to.z()) != Terrain.AIR) {
            return false; // head blocked
        }
        if (dy == 1 && world.terrainAt(from.x(), from.y() + 2, from.z()) != Terrain.AIR) {
            return false; // no room to jump from here
        }
        if (dy < 0) {
            // While dropping, the body passes down the target column from the start height.
            for (int y = to.y() + 2; y <= from.y() + 1; y++) {
                if (world.terrainAt(to.x(), y, to.z()) != Terrain.AIR) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int moveCost(int dy) {
        if (dy == 0) {
            return WALK_COST;
        }
        if (dy == 1) {
            return CLIMB_COST;
        }
        return DROP_BASE_COST + DROP_COST_PER_BLOCK * -dy;
    }

    private static int heuristic(Cell from, Cell goal) {
        return WALK_COST * (Math.abs(from.x() - goal.x()) + Math.abs(from.z() - goal.z()))
                + HEIGHT_COST * Math.abs(from.y() - goal.y());
    }

    private static boolean isGoal(Cell cell, Cell goal, int tolerance) {
        return Math.abs(cell.x() - goal.x()) <= tolerance
                && Math.abs(cell.z() - goal.z()) <= tolerance
                && Math.abs(cell.y() - goal.y()) <= 1;
    }

    private static List<Cell> rebuild(Node last) {
        List<Cell> path = new ArrayList<>();
        for (Node node = last; node != null; node = node.parent()) {
            path.add(node.cell());
        }
        Collections.reverse(path);
        return path;
    }

    private record Node(Cell cell, Node parent, int g, int f) {}
}
