package com.slushybear.daedalus.pathfinding;

/** Reach a standing height, at any position. Useful for climbing out of a hole or going down to a mining level. */
public record GoalYLevel(int y) implements Goal {
    @Override
    public boolean isEnd(Cell cell) {
        return cell.y() == y;
    }

    @Override
    public double heuristic(Cell cell) {
        return Goal.vertical(y - cell.y());
    }
}
