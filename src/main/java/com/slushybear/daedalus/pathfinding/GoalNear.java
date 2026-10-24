package com.slushybear.daedalus.pathfinding;

/** Get within `radius` blocks (straight-line) of a cell. Useful for following, or standing near something. */
public record GoalNear(Cell target, int radius) implements Goal {
    @Override
    public boolean isEnd(Cell cell) {
        long dx = cell.x() - target.x();
        long dy = cell.y() - target.y();
        long dz = cell.z() - target.z();
        return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
    }

    @Override
    public double heuristic(Cell cell) {
        // No move covers more horizontal distance than it pays for at sprint cost, and height only adds, so this is a lower bound.
        double gap = Math.hypot(cell.x() - target.x(), cell.z() - target.z()) - radius;
        return Math.max(0, gap) * Costs.minFlat();
    }
}
