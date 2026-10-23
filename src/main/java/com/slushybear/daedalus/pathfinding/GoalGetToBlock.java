package com.slushybear.daedalus.pathfinding;

/**
 * Stand where a block is within easy reach without standing in it: beside it at foot or head height, on top of it, or
 * directly under it. Useful for chests, crafting tables, or a block to mine or place against.
 */
public record GoalGetToBlock(Cell target) implements Goal {
    @Override
    public boolean isEnd(Cell cell) {
        int dx = Math.abs(cell.x() - target.x());
        int dz = Math.abs(cell.z() - target.z());
        int dy = cell.y() - target.y();
        if (dx + dz == 1) {
            return dy == 0 || dy == -1; // beside it, block at the feet or at the head
        }
        if (dx == 0 && dz == 0) {
            return dy == 1 || dy == -2; // standing on it, or right under it
        }
        return false;
    }

    @Override
    public double heuristic(Cell cell) {
        int dx = Math.max(0, Math.abs(cell.x() - target.x()) - 1);
        int dz = Math.max(0, Math.abs(cell.z() - target.z()) - 1);
        int dy = target.y() - cell.y();
        // Accepted heights run from 2 below the block to 1 above it.
        int beyond = dy > 2 ? dy - 2 : dy < -1 ? dy + 1 : 0;
        return Goal.flat(dx, dz) + Goal.vertical(beyond);
    }
}
