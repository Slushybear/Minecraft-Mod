package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StartCellAndNavigatorTest {
    @Test
    void feetCellAllowsForSoulSandAndFarmland() {
        assertEquals(new Cell(3, 65, 7), StartCell.feetCell(3.5, 65.0, 7.5));
        assertEquals(new Cell(3, 65, 7), StartCell.feetCell(3.5, 64.875, 7.5), "standing on soul sand");
        assertEquals(new Cell(3, 65, 7), StartCell.feetCell(3.5, 64.9375, 7.5), "standing on farmland");
    }

    @Test
    void standingNormallyStartsWhereTheFeetAre() {
        FakeWorld world = new FakeWorld().floor(0, 0, 3, 0, 3);

        assertEquals(new Cell(1, 1, 1), StartCell.choose(world, 1.5, 1, 1.5, true));
    }

    @Test
    void standingOnTheEdgeStartsFromTheBlockHoldingYouUp() {
        // The block at x=0 holds the player up, but their centre has crossed into x=1 over empty air.
        FakeWorld world = new FakeWorld().floor(0, 0, 0, 0, 0);

        assertEquals(new Cell(0, 1, 0), StartCell.choose(world, 1.2, 1, 0.5, true));
    }

    @Test
    void midJumpStartsFromTheGroundBelow() {
        FakeWorld world = new FakeWorld().floor(0, 0, 3, 0, 3);

        assertEquals(new Cell(1, 1, 1), StartCell.choose(world, 1.5, 2.1, 1.5, false));
    }

    @Test
    void aFarGoalIsReachedOneSegmentAtATime() {
        // Loaded ground only reaches x=30. The goal is at x=200, beyond it.
        BlockView world = (x, y, z) -> x > 30 ? Terrain.UNLOADED : y == 0 ? Terrain.SOLID : Terrain.AIR;
        Goal goal = new GoalBlock(new Cell(200, 1, 0), 0);

        Navigator.Plan plan = Navigator.plan(world, new Cell(0, 1, 0), goal, PathSettings.defaults().withoutTimeLimits()
                .withMaxNodes(5000), Set.of(), null).orElseThrow();

        assertFalse(plan.reachesGoal());
        assertTrue(plan.path().last().x() >= 25, "the segment should push toward the goal, ended at " + plan.path().last());
        PathSearchTest.assertLegal(world, plan.path().cells());
    }

    @Test
    void aReplanStaysOnThePreviousRouteWhereItCan() {
        FakeWorld world = new FakeWorld().floor(0, 0, 8, 0, 2).wall(1, 7, 1, 1);
        Goal goal = new GoalBlock(new Cell(8, 1, 1), 0);
        Path previous = new Path(List.of(
                new Waypoint(new Cell(0, 1, 1), MoveType.WALK, 0),
                new Waypoint(new Cell(1, 1, 2), MoveType.DIAGONAL, 5),
                new Waypoint(new Cell(4, 1, 2), MoveType.WALK, 5),
                new Waypoint(new Cell(7, 1, 2), MoveType.WALK, 5)));

        Navigator.Plan plan = Navigator.plan(world, new Cell(0, 1, 1), goal, PathSettings.defaults().withoutTimeLimits(),
                Set.of(), previous).orElseThrow();

        assertTrue(plan.reachesGoal());
        assertTrue(plan.path().cells().contains(new Cell(4, 1, 2)), "should keep to the north lane it was already on");
    }
}
