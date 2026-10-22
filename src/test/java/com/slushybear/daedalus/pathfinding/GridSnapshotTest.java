package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GridSnapshotTest {
    /** Flat floor at y=0, with a wall of solid blocks at x=5 for z in -2..2. */
    private static final BlockView WORLD = (x, y, z) -> {
        if (y == 0) {
            return Terrain.SOLID;
        }
        boolean wall = x == 5 && z >= -2 && z <= 2 && (y == 1 || y == 2);
        return wall ? Terrain.SOLID : Terrain.AIR;
    };

    @Test
    void copiesCellsInsideTheBox() {
        GridSnapshot snapshot = GridSnapshot.capture(WORLD, -3, -1, -3, 8, 4, 3);

        assertEquals(Terrain.SOLID, snapshot.terrainAt(0, 0, 0));
        assertEquals(Terrain.AIR, snapshot.terrainAt(0, 1, 0));
        assertEquals(Terrain.SOLID, snapshot.terrainAt(5, 1, 0));
    }

    @Test
    void cellsOutsideTheBoxAreUnloaded() {
        GridSnapshot snapshot = GridSnapshot.capture(WORLD, 0, 0, 0, 3, 3, 3);

        assertEquals(Terrain.UNLOADED, snapshot.terrainAt(-1, 0, 0));
        assertEquals(Terrain.UNLOADED, snapshot.terrainAt(4, 0, 0));
        assertEquals(Terrain.UNLOADED, snapshot.terrainAt(0, 4, 0));
        assertEquals(Terrain.UNLOADED, snapshot.terrainAt(0, 0, -1));
    }

    @Test
    void laterChangesToTheSourceDoNotAppear() {
        boolean[] open = {true};
        BlockView changing = (x, y, z) -> y == 0 ? Terrain.SOLID : (open[0] ? Terrain.AIR : Terrain.SOLID);

        GridSnapshot snapshot = GridSnapshot.capture(changing, 0, 0, 0, 2, 2, 2);
        open[0] = false;

        assertEquals(Terrain.AIR, snapshot.terrainAt(1, 1, 1));
    }

    @Test
    void rejectsAnEmptyOrHugeBox() {
        assertThrows(IllegalArgumentException.class, () -> GridSnapshot.capture(WORLD, 1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> GridSnapshot.capture(WORLD, 0, 0, 0, 2000, 2000, 2000));
    }

    @Test
    void searchingTheSnapshotMatchesSearchingTheSource() {
        Cell start = new Cell(0, 1, 0);
        Cell goal = new Cell(10, 1, 0);
        GridSnapshot snapshot = GridSnapshot.capture(WORLD, -6, -1, -10, 16, 6, 10);

        Optional<List<Cell>> direct = PathSearch.find(WORLD, start, goal, 0, 20000);
        Optional<List<Cell>> viaSnapshot = PathSearch.find(snapshot, start, goal, 0, 20000);

        assertTrue(direct.isPresent());
        assertEquals(direct, viaSnapshot);
        assertFalse(viaSnapshot.orElseThrow().contains(new Cell(5, 1, 0)), "must walk around the wall");
    }

    @Test
    void snapshotBoundsKeepTheSearchInsideTheBox() {
        // The box is too narrow to go around the wall, so there is no path inside it.
        GridSnapshot narrow = GridSnapshot.capture(WORLD, -1, -1, -1, 11, 4, 1);

        assertTrue(PathSearch.find(narrow, new Cell(0, 1, 0), new Cell(10, 1, 0), 0, 20000).isEmpty());
    }

    @Test
    void aSearchStopsWhenItsThreadIsInterrupted() throws InterruptedException {
        BlockView unreachable = (x, y, z) -> y == 0 ? Terrain.SOLID : Terrain.AIR;
        AtomicReference<Optional<List<Cell>>> result = new AtomicReference<>();

        // No node cap, and a goal nine blocks up that no move can reach, so only the interrupt can end this.
        Thread worker = new Thread(() -> {
            Thread.currentThread().interrupt();
            result.set(PathSearch.find(unreachable, new Cell(0, 1, 0), new Cell(20, 10, 0), 0, Integer.MAX_VALUE));
        });
        worker.start();
        worker.join(5000);

        assertFalse(worker.isAlive(), "search ignored the interrupt");
        assertTrue(result.get().isEmpty());
    }
}
