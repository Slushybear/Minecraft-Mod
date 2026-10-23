package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GoalAndCostTest {
    private static final int CAP = 20000;

    @Test
    void costsMatchVanillaSpeeds() {
        assertEquals(4.633, Costs.WALK, 0.001);
        assertEquals(3.564, Costs.SPRINT, 0.001);
        // A jump peaks at 1.25 blocks; the time to rise one block of it is about 3.16 ticks.
        assertEquals(3.16, Costs.JUMP_UP, 0.02);
        // The first tick from a standstill moves nothing, so one block takes about 5.6 ticks and three about 9.5.
        assertEquals(5.61, Costs.fall(1), 0.02);
        assertEquals(9.47, Costs.fall(3), 0.05);
        assertTrue(Costs.fall(20) > Costs.fall(3));
    }

    /**
     * The heuristics assume every move costs at least the cheapest possible way to cover its ground. Check that claim
     * against every move the rules generate in a world with stairs, gaps, ladders, water and long falls.
     */
    @Test
    void everyMoveCostsAtLeastWhatTheHeuristicsAssume() {
        FakeWorld world = new FakeWorld()
                .floor(0, 0, 30, 0, 6)
                .floor(1, 4, 6, 0, 6).floor(2, 6, 6, 0, 6)              // stairs
                .fill(Terrain.AIR, 9, 9, 0, 0, 0, 6)                    // a 1-block gap
                .fill(Terrain.AIR, 12, 14, 0, 0, 0, 6)                  // a 3-block gap
                .floor(1, 16, 18, 0, 6)                                 // a ledge to jump up onto
                .fill(Terrain.CLIMBABLE, 20, 20, 1, 6, 3, 3).floor(6, 21, 22, 0, 6)  // ladder to a high platform
                .floor(20, 25, 26, 0, 6).fill(Terrain.WATER, 27, 27, 1, 2, 0, 6)     // high cliff over water
                .fill(Terrain.WATER, 28, 30, 1, 1, 0, 6);

        int checked = 0;
        for (int x = -1; x <= 31; x++) {
            for (int y = 0; y <= 22; y++) {
                for (int z = -1; z <= 7; z++) {
                    Cell from = new Cell(x, y, z);
                    if (Moves.kind(world, from) == Moves.Kind.NONE) {
                        continue;
                    }
                    for (Moves.Move move : Moves.from(world, from, 0, PathSettings.defaults())) {
                        Cell to = move.to();
                        double bound = Goal.flat(to.x() - x, to.z() - z) + Goal.vertical(to.y() - y);
                        assertTrue(move.cost() >= bound - 1e-9,
                                move.type() + " from " + from + " to " + to + " costs " + move.cost() + " < " + bound);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 500, "the world should produce plenty of moves to check, got " + checked);
    }

    @Test
    void heuristicsNeverOverestimateRealRoutes() {
        FakeWorld world = new FakeWorld().floor(0, 0, 10, 0, 4).floor(1, 6, 10, 0, 4).wall(3, 3, 0, 2);
        Cell target = new Cell(10, 2, 4);
        List<Goal> goals = List.of(new GoalBlock(target, 0), new GoalBlock(target, 2), new GoalNear(target, 3),
                new GoalGetToBlock(new Cell(10, 1, 4)), new GoalYLevel(2),
                new GoalComposite(List.of(new GoalBlock(new Cell(0, 1, 4), 0), new GoalBlock(target, 0))));

        for (Goal goal : goals) {
            for (int x = 0; x <= 5; x++) {
                for (int z = 0; z <= 4; z++) {
                    Cell from = new Cell(x, 1, z);
                    if (Moves.kind(world, from) != Moves.Kind.LAND) {
                        continue;
                    }
                    Path path = PathSearch.findPath(world, from, goal, CAP, Set.of()).orElseThrow();
                    assertTrue(goal.heuristic(from) <= path.cost() + 1e-9,
                            goal + " overestimates from " + from + ": " + goal.heuristic(from) + " > " + path.cost());
                }
            }
        }
    }

    @Test
    void excludedCellForcesADetour() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 1);
        Cell blocked = new Cell(3, 1, 0);

        List<Cell> path = PathSearch.find(world, new Cell(0, 1, 0), new GoalBlock(new Cell(5, 1, 0), 0), CAP, Set.of(blocked))
                .orElseThrow();

        assertFalse(path.contains(blocked));
        assertEquals(new Cell(5, 1, 0), path.get(path.size() - 1));
    }

    @Test
    void excludingTheOnlyRouteGivesNoPath() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0);

        assertTrue(PathSearch.find(world, new Cell(0, 1, 0), new GoalBlock(new Cell(5, 1, 0), 0), CAP,
                Set.of(new Cell(3, 1, 0))).isEmpty());
    }

    @Test
    void prefersALaneAwayFromLava() {
        FakeWorld world = new FakeWorld().floor(0, 0, 6, 0, 2).set(3, 1, 0, Terrain.DANGER);

        List<Cell> path = PathSearch.find(world, new Cell(0, 1, 1), new Cell(6, 1, 1), 0, CAP).orElseThrow();

        assertFalse(path.contains(new Cell(3, 1, 1)), "should not walk beside the lava when a safe lane exists");
        assertTrue(path.contains(new Cell(3, 1, 2)));
    }

    @Test
    void stillWalksBesideLavaWhenThereIsNoOtherWay() {
        FakeWorld world = new FakeWorld().floor(0, 0, 6, 0, 1).set(3, 1, 0, Terrain.DANGER);

        List<Cell> path = PathSearch.find(world, new Cell(0, 1, 1), new Cell(6, 1, 1), 0, CAP).orElseThrow();

        assertTrue(path.contains(new Cell(3, 1, 1)), "a penalty is a preference, not a ban");
    }

    @Test
    void toleranceStopsAtTheFirstAcceptedCell() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0);

        List<Cell> path = PathSearch.find(world, new Cell(0, 1, 0), new Cell(5, 1, 0), 2, CAP).orElseThrow();

        assertEquals(new Cell(3, 1, 0), path.get(path.size() - 1));
        assertEquals(4, path.size());
    }

    @Test
    void goalNearStopsWithinTheRadius() {
        FakeWorld world = new FakeWorld().floor(0, 0, 8, 0, 0);
        Cell target = new Cell(8, 1, 0);

        List<Cell> path = PathSearch.find(world, new Cell(0, 1, 0), new GoalNear(target, 3), CAP, Set.of()).orElseThrow();

        assertEquals(new Cell(5, 1, 0), path.get(path.size() - 1), "should stop as soon as it is within 3 blocks");
    }

    @Test
    void goalYLevelClimbsStairs() {
        FakeWorld world = new FakeWorld().floor(0, 0, 1, 0, 0).floor(1, 2, 2, 0, 0).floor(2, 3, 3, 0, 0).floor(3, 4, 4, 0, 0);

        List<Cell> path = PathSearch.find(world, new Cell(0, 1, 0), new GoalYLevel(4), CAP, Set.of()).orElseThrow();

        assertEquals(4, path.get(path.size() - 1).y());
    }

    @Test
    void goalYLevelFindsTheWayDown() {
        FakeWorld world = new FakeWorld().floor(3, 0, 1, 0, 0).floor(2, 2, 2, 0, 0).floor(1, 3, 3, 0, 0).floor(0, 4, 5, 0, 0);

        List<Cell> path = PathSearch.find(world, new Cell(0, 4, 0), new GoalYLevel(1), CAP, Set.of()).orElseThrow();

        assertEquals(1, path.get(path.size() - 1).y());
    }

    @Test
    void goalYLevelFailsWhenNoRouteReachesIt() {
        FakeWorld world = new FakeWorld().floor(0, 0, 5, 0, 0);

        assertTrue(PathSearch.find(world, new Cell(0, 1, 0), new GoalYLevel(10), 2000, Set.of()).isEmpty());
    }

    @Test
    void compositeGoalPicksTheCheaperTarget() {
        FakeWorld world = new FakeWorld().floor(0, 0, 20, 0, 0);
        Goal either = new GoalComposite(List.of(new GoalBlock(new Cell(20, 1, 0), 0), new GoalBlock(new Cell(3, 1, 0), 0)));

        List<Cell> path = PathSearch.find(world, new Cell(0, 1, 0), either, CAP, Set.of()).orElseThrow();

        assertEquals(new Cell(3, 1, 0), path.get(path.size() - 1));
    }

    @Test
    void getToBlockStopsBesideTheBlock() {
        // A chest-like block at (5,1,0): the route should end next to it, not try to stand in it.
        FakeWorld world = new FakeWorld().floor(0, 0, 8, 0, 0).set(5, 1, 0, Terrain.SOLID);

        List<Cell> path = PathSearch.find(world, new Cell(0, 1, 0), new GoalGetToBlock(new Cell(5, 1, 0)), CAP, Set.of())
                .orElseThrow();

        assertEquals(new Cell(4, 1, 0), path.get(path.size() - 1));
    }
}
