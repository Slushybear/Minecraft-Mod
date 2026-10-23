package com.slushybear.daedalus.pathfinding;

import java.util.List;

/** An ordered route. The first waypoint is where the walk starts. */
public record Path(List<Waypoint> waypoints) {
    public Path {
        waypoints = List.copyOf(waypoints);
    }

    public List<Cell> cells() {
        return waypoints.stream().map(Waypoint::cell).toList();
    }

    public Cell start() {
        return waypoints.get(0).cell();
    }

    public Cell last() {
        return waypoints.get(waypoints.size() - 1).cell();
    }

    public int size() {
        return waypoints.size();
    }

    /** Total expected time in ticks. */
    public double cost() {
        return waypoints.stream().mapToDouble(Waypoint::cost).sum();
    }
}
