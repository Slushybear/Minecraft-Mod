package com.slushybear.daedalus.pathfinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps track of where the player is along a route, from their position alone. Pure logic, so it is tested without the
 * game. The walker asks it every tick which move is current, and whether to give up.
 *
 * <p>Each move has a set of cells the player may legitimately be in while making it (start, end, plus the corners of a
 * diagonal, the column of a fall, the arc of a jump). If the player is in an earlier move's cells (lag, knocked back) or
 * a later one's (skipped ahead, flew over), the tracker jumps there instead of failing. Off route means far from every
 * cell of the whole route: a little drift is tolerated for a while, a lot ends the route at once. Each move also gets a
 * time limit of its expected ticks plus a grace period.
 */
public final class PathTracker {
    public enum Status { ON_TRACK, FINISHED, OFF_PATH, TIMED_OUT }

    /** Further than this from every cell of the route counts as drifting, which is tolerated for a while. */
    public static final double DRIFT_DISTANCE = 2;
    /** Further than this is lost, and the route is abandoned at once. */
    public static final double LOST_DISTANCE = 3;
    public static final int MAX_DRIFT_TICKS = 200;
    /** A move may take its expected ticks plus this many before it counts as stuck. */
    public static final int TIMEOUT_GRACE_TICKS = 100;

    private final List<Waypoint> waypoints;
    private final List<Set<Cell>> validCells = new ArrayList<>();
    private int index;
    private int ticksOnMove;
    private int ticksDrifting;
    private int resyncs;

    public PathTracker(Path path) {
        this.waypoints = path.waypoints();
        for (int i = 0; i + 1 < waypoints.size(); i++) {
            validCells.add(validCellsFor(waypoints.get(i).cell(), waypoints.get(i + 1)));
        }
    }

    /** Index of the current move: from waypoint `index` to waypoint `index + 1`. */
    public int moveIndex() {
        return index;
    }

    public boolean finished() {
        return index >= waypoints.size() - 1;
    }

    public Waypoint from() {
        return waypoints.get(index);
    }

    public Waypoint to() {
        return waypoints.get(index + 1);
    }

    /** The waypoint `steps` moves after the current target, or null past the end. */
    public Waypoint ahead(int steps) {
        int i = index + 2 + steps;
        return i < waypoints.size() ? waypoints.get(i) : null;
    }

    /** How many times the tracker moved its place along the route to match the player. For the log. */
    public int resyncs() {
        return resyncs;
    }

    /**
     * Call once per tick with the player's position.
     *
     * @param supported standing on something, in water, or on a ladder: anything but mid-air
     */
    public Status update(double x, double y, double z, boolean supported) {
        if (finished()) {
            return Status.FINISHED;
        }
        Cell feet = StartCell.feetCell(x, y, z);
        if (arrived(feet, y, supported)) {
            advance(index + 1);
            if (finished()) {
                return Status.FINISHED;
            }
        }
        if (!validCells.get(index).contains(feet)) {
            resync(feet);
            if (finished()) {
                return Status.FINISHED;
            }
        }

        double distance = distanceToRoute(x, y, z);
        boolean falling = to().type() == MoveType.DROP && flatDistance(x, z, to().cell()) < DRIFT_DISTANCE;
        if (!falling && distance > LOST_DISTANCE) {
            return Status.OFF_PATH;
        }
        if (!falling && distance > DRIFT_DISTANCE) {
            if (++ticksDrifting > MAX_DRIFT_TICKS) {
                return Status.OFF_PATH;
            }
        } else {
            ticksDrifting = 0;
        }

        if (++ticksOnMove > to().cost() + TIMEOUT_GRACE_TICKS) {
            return Status.TIMED_OUT;
        }
        return Status.ON_TRACK;
    }

    /**
     * True if stopping right now leaves the player somewhere safe: not mid-air, and not halfway into a jump they have
     * committed to. Replanning only happens when this is true.
     */
    public boolean safeToCancel(double x, double y, double z, boolean supported) {
        if (finished()) {
            return true;
        }
        if (!supported) {
            return false;
        }
        return to().type() != MoveType.PARKOUR || StartCell.feetCell(x, y, z).equals(from().cell());
    }

    private boolean arrived(Cell feet, double y, boolean supported) {
        Cell target = to().cell();
        return feet.equals(target) && (supported || y - target.y() < 0.5);
    }

    private void advance(int newIndex) {
        index = newIndex;
        ticksOnMove = 0;
    }

    /** Looks back first (lag, knockback), then forward (skipped ahead), for a move whose cells hold the player. */
    private void resync(Cell feet) {
        for (int i = 0; i < index; i++) {
            if (validCells.get(i).contains(feet)) {
                advance(i);
                resyncs++;
                return;
            }
        }
        for (int i = index + 1; i < validCells.size(); i++) {
            if (validCells.get(i).contains(feet)) {
                advance(i);
                resyncs++;
                return;
            }
        }
    }

    /** Distance from the feet to the bottom centre of the nearest cell anywhere on the route. */
    private double distanceToRoute(double x, double y, double z) {
        double best = Double.MAX_VALUE;
        for (Set<Cell> cells : validCells) {
            for (Cell cell : cells) {
                double dx = cell.x() + 0.5 - x;
                double dy = cell.y() - y;
                double dz = cell.z() + 0.5 - z;
                best = Math.min(best, dx * dx + dy * dy + dz * dz);
            }
        }
        return Math.sqrt(best);
    }

    private static double flatDistance(double x, double z, Cell cell) {
        return Math.hypot(cell.x() + 0.5 - x, cell.z() + 0.5 - z);
    }

    /** The cells the body passes through while making one move. */
    static Set<Cell> validCellsFor(Cell a, Waypoint to) {
        Cell b = to.cell();
        Set<Cell> cells = new HashSet<>();
        cells.add(a);
        cells.add(b);
        switch (to.type()) {
            case DIAGONAL -> {
                cells.add(new Cell(b.x(), a.y(), a.z()));
                cells.add(new Cell(a.x(), a.y(), b.z()));
            }
            case CLIMB -> cells.add(new Cell(a.x(), a.y() + 1, a.z()));
            case DROP -> {
                for (int y = b.y(); y <= a.y(); y++) {
                    cells.add(new Cell(b.x(), y, b.z()));
                }
            }
            case PARKOUR -> {
                int dx = Integer.signum(b.x() - a.x());
                int dz = Integer.signum(b.z() - a.z());
                int distance = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
                for (int k = 0; k <= distance; k++) {
                    cells.add(new Cell(a.x() + dx * k, a.y(), a.z() + dz * k));
                    cells.add(new Cell(a.x() + dx * k, a.y() + 1, a.z() + dz * k));
                }
            }
            case SWIM, LADDER -> {
                cells.add(new Cell(a.x(), a.y() + 1, a.z()));
                cells.add(new Cell(b.x(), b.y() + 1, b.z()));
            }
            default -> { }
        }
        return cells;
    }
}
