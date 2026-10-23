package com.slushybear.daedalus.pathfinding;

/**
 * A cell on a path, how the walker gets there from the previous waypoint, and how many ticks that move should take.
 * The first waypoint of a path is where the walk starts, with a cost of 0.
 */
public record Waypoint(Cell cell, MoveType type, double cost) {}
