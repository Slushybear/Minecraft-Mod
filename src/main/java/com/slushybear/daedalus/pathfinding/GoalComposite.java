package com.slushybear.daedalus.pathfinding;

import java.util.List;

/** Any one of several goals. The search heads for whichever is cheapest to reach, e.g. the nearest of many ores. */
public record GoalComposite(List<Goal> goals) implements Goal {
    public GoalComposite {
        goals = List.copyOf(goals);
    }

    @Override
    public boolean isEnd(Cell cell) {
        for (Goal goal : goals) {
            if (goal.isEnd(cell)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double heuristic(Cell cell) {
        // The cheapest of lower bounds is still a lower bound for reaching any of them.
        double best = Double.POSITIVE_INFINITY;
        for (Goal goal : goals) {
            best = Math.min(best, goal.heuristic(cell));
        }
        return goals.isEmpty() ? 0 : best;
    }
}
