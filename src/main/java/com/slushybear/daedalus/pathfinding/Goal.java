package com.slushybear.daedalus.pathfinding;

/** Where a search should end up. Implementations decide when the search is done and estimate what is left, in ticks. */
public interface Goal {
    boolean isEnd(Cell cell);

    /** Lower bound on the ticks from this cell to the goal. It must never overestimate, or A* can return a worse route. */
    double heuristic(Cell cell);

    /**
     * Cheapest possible ticks for a horizontal offset: sprinting straight, or diagonally at sqrt 2 per cell, whichever
     * covers it. Every move costs at least this for the ground it covers.
     */
    static double flat(double dx, double dz) {
        double a = Math.abs(dx);
        double b = Math.abs(dz);
        double straight = Math.abs(a - b);
        double diagonal = Math.min(a, b);
        return (straight + diagonal * Math.sqrt(2)) * Costs.minFlat();
    }

    /** Cheapest possible ticks to change height by dy (positive is up). */
    static double vertical(int dy) {
        return dy > 0 ? dy * Costs.MIN_PER_BLOCK_UP : -dy * Costs.MIN_PER_BLOCK_DOWN;
    }
}
