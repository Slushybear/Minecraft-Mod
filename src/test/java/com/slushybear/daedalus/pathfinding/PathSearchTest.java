package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PathSearchTest {
    private static final int NODE_CAP = 20000;

    private static Optional<List<Cell>> search(BlockView world, Cell start, Cell goal) {
        return PathSearch.find(world, start, goal, 0, NODE_CAP);
    }

    /** Every step of a route must be a legal move under the default rules. */
    static void assertLegal(BlockView world, List<Cell> path) {
        for (int i = 1; i < path.size(); i++) {
            assertTrue(Moves.isPossible(world, path.get(i - 1), path.get(i), PathSettings.defaults()),
                    "step " + i + " from " + path.get(i - 1) + " to " + path.get(i) + " is not a legal move");
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
        assertLegal(world, path);
    }

    @Test
    void startEqualsGoalGivesOneCell() {
        FakeWorld world = new FakeWorld().floor(0, 0, 0, 0, 0);
        Cell start = new Cell(0, 1, 0);

        assertEquals(List.of(start), search(world, start, start).orElseThrow());
    }

    @Test
    void climbsOneBlock() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).floor(1, 3, 5, 0, 0);
        Cell goal = new Cell(5, 2, 0);

        List<Cell> path = search(world, new Cell(0, 1, 0), goal).orElseThrow();

        assertEquals(goal, path.get(path.size() - 1));
        assertTrue(path.contains(new Cell(3, 2, 0)), "path should step up onto the shelf");
        assertLegal(world, path);
    }

    @Test
    void cannotClimbTwoBlocks() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).floor(1, 3, 5, 0, 0).floor(2, 3, 5, 0, 0);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(5, 3, 0)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void dropsUpToThreeBlocks(int drop) {
        FakeWorld world = new FakeWorld().floor(drop, 0, 2, 0, 0).floor(0, 3, 6, 0, 0);
        Cell goal = new Cell(6, 1, 0);

        List<Cell> path = search(world, new Cell(0, drop + 1, 0), goal).orElseThrow();

        assertEquals(goal, path.get(path.size() - 1));
        assertLegal(world, path);
    }

    @Test
    void rejectsFourBlockDropOntoGround() {
        FakeWorld world = new FakeWorld().floor(4, 0, 2, 0, 0).floor(0, 3, 6, 0, 0);

        assertTrue(search(world, new Cell(0, 5, 0), new Cell(6, 1, 0)).isEmpty());
    }

    @Test
    void jumpsAOneBlockGap() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 4, 6, 0, 0);

        List<Cell> path = search(world, new Cell(0, 1, 0), new Cell(6, 1, 0)).orElseThrow();

        assertTrue(path.contains(new Cell(2, 1, 0)) && path.contains(new Cell(4, 1, 0)));
        assertFalse(path.contains(new Cell(3, 1, 0)), "cannot stand in the gap");
        assertLegal(world, path);
    }

    @Test
    void goesAroundAGapWhenJumpingIsOff() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 4, 6, 0, 0).floor(0, 2, 4, 1, 1);
        PathSettings noJumps = PathSettings.defaults().withParkour(false).withMaxNodes(NODE_CAP);

        Path path = PathSearch.findPath(world, new Cell(0, 1, 0), new GoalBlock(new Cell(6, 1, 0), 0), noJumps, Set.of())
                .orElseThrow();

        assertFalse(path.cells().contains(new Cell(3, 1, 0)), "must not step over the gap");
        assertTrue(path.cells().contains(new Cell(3, 1, 1)), "should use the side lane");
    }

    @Test
    void needsAFloorUnderTheGoal() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(4, 1, 0)).isEmpty());
    }

    @Test
    void lowCeilingBlocksTheBody() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).set(3, 2, 0, Terrain.SOLID);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(5, 1, 0)).isEmpty());
    }

    @Test
    void fencesAndSlabsAreNotFloors() {
        // A BLOCKED cell (fence, wall, bottom slab) is in the way but cannot be stood on at block height.
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).set(3, 0, 0, Terrain.BLOCKED).floor(0, 4, 6, 0, 0);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(6, 1, 0)).isEmpty());
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
        assertLegal(world, path);
    }

    @Test
    void unloadedChunksAreBlocked() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0).set(3, 0, 0, Terrain.UNLOADED);

        assertTrue(search(world, new Cell(0, 1, 0), new Cell(5, 1, 0)).isEmpty());
    }

    @Test
    void stopsAtTheNodeCapOnAnUnreachableGoal() {
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

        assertEquals(new Cell(3, 1, 0), path.get(path.size() - 1));
    }

    @Test
    void aFarGoalGivesAPartialSegmentWhenTimeOrNodesRunOut() {
        // An endless floor and a goal 500 blocks away: 200 nodes is not enough to get there, so a partial route comes back.
        BlockView world = (x, y, z) -> y == 0 ? Terrain.SOLID : Terrain.AIR;
        PathSettings settings = PathSettings.defaults().withMaxNodes(200).withoutTimeLimits();

        PathSearch.Result result = PathSearch.search(world, new Cell(0, 1, 0), new GoalBlock(new Cell(500, 1, 0), 0),
                settings, Set.of(), Set.of());

        assertFalse(result.reachedGoal());
        assertTrue(result.path() != null && result.path().last().x() > 5, "should head toward the goal");
        assertLegal(world, result.path().cells());
    }

    @Test
    void noPartialRouteWhenNothingGetsFiveBlocksAway() {
        // Boxed in: nothing to walk to, so there is no segment worth returning.
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0);

        PathSearch.Result result = PathSearch.search(world, new Cell(0, 1, 0), new GoalBlock(new Cell(50, 1, 0), 0),
                PathSettings.defaults().withoutTimeLimits(), Set.of(), Set.of());

        assertFalse(result.reachedGoal());
        assertTrue(result.path() == null);
    }

    @Test
    void walksDiagonallyOnOpenGround() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 5);

        Path path = PathSearch.findPath(world, new Cell(0, 1, 0), new GoalBlock(new Cell(5, 1, 5), 0), NODE_CAP, Set.of())
                .orElseThrow();

        assertEquals(6, path.size(), "five diagonal steps, not a ten-step staircase");
        assertTrue(path.waypoints().stream().skip(1).allMatch(w -> w.type() == MoveType.DIAGONAL));
    }

    @Test
    void favouredCellsArePreferred() {
        // Two equally good lanes. Favouring the north lane (the previous route) should keep the plan on it.
        FakeWorld world = new FakeWorld().floor(0, 0, 8, 0, 2).wall(1, 7, 1, 1);
        Cell start = new Cell(0, 1, 1);
        Cell goal = new Cell(8, 1, 1);
        Set<Cell> northLane = Set.of(new Cell(1, 1, 2), new Cell(2, 1, 2), new Cell(3, 1, 2), new Cell(4, 1, 2),
                new Cell(5, 1, 2), new Cell(6, 1, 2), new Cell(7, 1, 2));

        PathSearch.Result result = PathSearch.search(world, start, new GoalBlock(goal, 0),
                PathSettings.defaults().withoutTimeLimits(), Set.of(), northLane);

        assertTrue(result.reachedGoal());
        assertTrue(result.path().cells().contains(new Cell(4, 1, 2)));
    }
}
