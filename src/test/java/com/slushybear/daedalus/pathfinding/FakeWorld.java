package com.slushybear.daedalus.pathfinding;

import java.util.HashMap;
import java.util.Map;

/** A world that is all air except what a test places. Standing needs a SOLID cell under the feet. */
final class FakeWorld implements BlockView {
    private final Map<Cell, Terrain> cells = new HashMap<>();

    FakeWorld set(int x, int y, int z, Terrain terrain) {
        cells.put(new Cell(x, y, z), terrain);
        return this;
    }

    FakeWorld fill(Terrain terrain, int fromX, int toX, int fromY, int toY, int fromZ, int toZ) {
        for (int x = fromX; x <= toX; x++) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    set(x, y, z, terrain);
                }
            }
        }
        return this;
    }

    /** Solid blocks at height y over a rectangle. Standing on them puts the feet at y + 1. */
    FakeWorld floor(int y, int fromX, int toX, int fromZ, int toZ) {
        return fill(Terrain.SOLID, fromX, toX, y, y, fromZ, toZ);
    }

    /** A two-high solid wall standing on floor height 0, at feet and head height 1 and 2. */
    FakeWorld wall(int fromX, int toX, int fromZ, int toZ) {
        return fill(Terrain.SOLID, fromX, toX, 1, 2, fromZ, toZ);
    }

    @Override
    public Terrain terrainAt(int x, int y, int z) {
        return cells.getOrDefault(new Cell(x, y, z), Terrain.AIR);
    }
}
