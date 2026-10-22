package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PathSearchTest {
    private static final int NODE_CAP = 20000;

    /** A world that is all air except what a test places. Standing needs a SOLID cell under the feet. */
    private static final class FakeWorld implements BlockView {
        private final Map<Cell, Terrain> cells = new HashMap<>();

        FakeWorld set(int x, int y, int z, Terrain terrain) {
            cells.put(new Cell(x, y, z), terrain);
            return this;
        }

        FakeWorld floor(int y, int fromX, int toX, int fromZ, int toZ) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    set(x, y, z, Terrain.SOLID);
                }
            }
            return this;
        }

        @Override
        public Terrain terrainAt(int x, int y, int z) {
            return cells.getOrDefault(new Cell(x, y, z), Terrain.AIR);
        }
    }

    private static Optional<List<Cell>> search(BlockView world, Cell start, Cell goal) {
        return PathSearch.find(world, start, goal, 0, NODE_CAP);
    }

    /** Every step must move one block sideways and change height by at most +1 or -3. */
    private static void assertContiguous(List<Cell> path) {
        for (int i = 1; i < path.size(); i++) {
            Cell a = path.get(i - 1);
            Cell b = path.get(i);
            assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()), "step " + i + " is not one block sideways");
            int dy = b.y() - a.y();
            assertTrue(dy >= -3 && dy <= 1, "step " + i + " changes height by " + dy);
        }
    }

    @Test
    void walksAcrossFlatGround() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0);
        Cell start = new Cell(0, 1, 0);
        Cell goal = new Cell(5, 1, 0);

        List<Cell> path = search(world, start, goal).orElseThrow();

        assertEquals(6, path.size());
        assertEquals(start, path.get(0));
        assertEquals(goal, path.get(path.size() - 1));
        assertContiguous(path);
    }

    @Test
    void startEqualsGoalGivesOneCell() {
        FakeWorld world = new FakeWorld().floor(0, 0, 0, 0, 0);
        Cell start = new Cell(0, 1, 0);

        assertEquals(List.of(start), search(world, start, start).orElseThrow());
    }

    @Test
    void climbsOneBlock() {
        // Ground at y=0, with a raised shelf (solid at y=1) from x=3 onward.
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).floor(1, 3, 5, 0, 0);
        Cell goal = new Cell(5, 2, 0);

        List<Cell> path = search(world, new Cell(0, 1, 0), goal).orElseThrow();

        assertEquals(goal, path.get(path.size() - 1));
        assertTrue(path.contains(new Cell(3, 2, 0)), "path should step up onto the shelf");
        assertContiguous(path);
    }

    @Test
    void cannotClimbTwoBlocks() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).floor(1, 3, 5, 0, 0).floor(2, 3, 5, 0, 0);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(5, 3, 0)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void dropsUpToThreeBlocks(int drop) {
        // High platform (floor at y=drop) for x 0..2, ground (y=0) for x 3..6.
        FakeWorld world = new FakeWorld().floor(drop, 0, 2, 0, 0).floor(0, 3, 6, 0, 0);
        Cell goal = new Cell(6, 1, 0);

        List<Cell> path = search(world, new Cell(0, drop + 1, 0), goal).orElseThrow();

        assertEquals(goal, path.get(path.size() - 1));
        assertContiguous(path);
    }

    @Test
    void rejectsFourBlockDrop() {
        FakeWorld world = new FakeWorld().floor(4, 0, 2, 0, 0).floor(0, 3, 6, 0, 0);

        assertTrue(search(world, new Cell(0, 5, 0), new Cell(6, 1, 0)).isEmpty());
    }

    @Test
    void cannotCrossAOneBlockGap() {
        // No floor at x=3. The walker has no parkour, so it must not invent a jump.
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 4, 6, 0, 0);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(6, 1, 0)).isEmpty());
    }

    @Test
    void goesAroundAGapWhenALaneExists() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 4, 6, 0, 0).floor(0, 2, 4, 1, 1);

        List<Cell> path = search(world, new Cell(0, 1, 0), new Cell(6, 1, 0)).orElseThrow();

        assertFalse(path.contains(new Cell(3, 1, 0)), "must not step over the gap");
        assertTrue(path.contains(new Cell(3, 1, 1)), "should use the side lane");
        assertContiguous(path);
    }

    @Test
    void needsAFloorUnderTheGoal() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(4, 1, 0)).isEmpty());
    }

    @Test
    void lowCeilingBlocksTheBody() {
        // Solid at y=2 over x=3 leaves one block of space, but the body needs two.
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).set(3, 2, 0, Terrain.SOLID);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(5, 1, 0)).isEmpty());
    }

    @Test
    void lavaBlocksTheOnlyRoute() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).set(3, 0, 0, Terrain.DANGER);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(5, 1, 0)).isEmpty());
    }

    @Test
    void lavaAtFeetBlocksTheOnlyRoute() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).set(3, 1, 0, Terrain.DANGER);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(5, 1, 0)).isEmpty());
    }

    @Test
    void routesAroundLavaWhenALaneExists() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 1).set(3, 0, 0, Terrain.DANGER);

        List<Cell> path = search(world, new Cell(0, 1, 0), new Cell(5, 1, 0)).orElseThrow();

        assertFalse(path.contains(new Cell(3, 1, 0)));
        assertContiguous(path);
    }

    @Test
    void unloadedChunksAreBlocked() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).set(3, 0, 0, Terrain.UNLOADED);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(5, 1, 0)).isEmpty());
    }

    @Test
    void stopsAtTheNodeCapOnAnUnreachableGoal() {
        // Infinite flat floor, and a goal on a block floating nine blocks up that no move can reach.
        BlockView world = (x, y, z) -> {
            if (y == 0) {
                return Terrain.SOLID;
            }
            return (x == 50 && y == 9 && z == 0) ? Terrain.SOLID : Terrain.AIR;
        };

        Optional<List<Cell>> result = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> PathSearch.find(world, new Cell(0, 1, 0), new Cell(50, 10, 0), 0, NODE_CAP));

        assertTrue(result.isEmpty());
    }

    @Test
    void aTinyCapGivesUpOnAReachableGoal() {
        FakeWorld world = new FakeWorld().floor(0, 0, 20, 0, 0);

        assertTrue(PathSearch.find(world, new Cell(0, 1, 0), new Cell(20, 1, 0), 0, 3).isEmpty());
    }

    @Test
    void toleranceAcceptsANearbyCell() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0);

        List<Cell> path = PathSearch.find(world, new Cell(0, 1, 0), new Cell(5, 1, 0), 2, NODE_CAP).orElseThrow();

        Cell last = path.get(path.size() - 1);
        assertTrue(Math.abs(last.x() - 5) <= 2, "should stop within 2 blocks of the goal, stopped at x=" + last.x());
        assertTrue(path.size() < 6, "should not walk all the way to the goal");
    }
}
