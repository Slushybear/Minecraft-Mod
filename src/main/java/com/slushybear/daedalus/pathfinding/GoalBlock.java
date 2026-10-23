package com.slushybear.daedalus.pathfinding;

/** Reach a cell, or stop within `tolerance` blocks sideways of it and within 1 block of its height. */
public record GoalBlock(Cell target, int tolerance) implements Goal {
    @Override
    public boolean isEnd(Cell cell) {
        return Math.abs(cell.x() - target.x()) <= tolerance
                && Math.abs(cell.z() - target.z()) <= tolerance
                && Math.abs(cell.y() - target.y()) <= 1;
    }

    @Override
    public double heuristic(Cell cell) {
        int dx = Math.max(0, Math.abs(cell.x() - target.x()) - tolerance);
        int dz = Math.max(0, Math.abs(cell.z() - target.z()) - tolerance);
        int dy = target.y() - cell.y();
        int beyondTolerance = dy > 1 ? dy - 1 : dy < -1 ? dy + 1 : 0;
        return Goal.flat(dx, dz) + Goal.vertical(beyondTolerance);
    }
}
