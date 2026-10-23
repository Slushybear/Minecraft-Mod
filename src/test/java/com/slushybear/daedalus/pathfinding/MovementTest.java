package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Jumping, diagonals, falls, swimming and ladders. */
class MovementTest {
    private static final int CAP = 20000;

    private static Path path(BlockView world, Cell start, Goal goal) {
        return PathSearch.findPath(world, start, goal, CAP, Set.of()).orElseThrow();
    }

    private static boolean noPath(BlockView world, Cell start, Goal goal, PathSettings settings) {
        return PathSearch.findPath(world, start, goal, settings.withMaxNodes(CAP), Set.of()).isEmpty();
    }

    private static boolean noPath(BlockView world, Cell start, Goal goal) {
        return noPath(world, start, goal, PathSettings.defaults());
    }

    private static long count(Path path, MoveType type) {
        return path.waypoints().stream().filter(w -> w.type() == type).count();
    }

    private static GoalBlock at(int x, int y, int z) {
        return new GoalBlock(new Cell(x, y, z), 0);
    }

    // ---------- jumping across gaps ----------

    @Test
    void jumpsATwoBlockGap() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 5, 7, 0, 0);

        Path path = path(world, new Cell(0, 1, 0), at(7, 1, 0));

        assertEquals(1, count(path, MoveType.PARKOUR));
        assertTrue(path.cells().contains(new Cell(5, 1, 0)));
    }

    @Test
    void jumpsATwoBlockGapOntoASingleBlock() {
        // A plain jump lands short enough for a one-block landing, so no extra landing room is needed.
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 5, 5, 0, 0);

        Path path = path(world, new Cell(0, 1, 0), at(5, 1, 0));

        assertEquals(1, count(path, MoveType.PARKOUR));
    }

    @Test
    void sprintJumpsAThreeBlockGapOnlyWithLongJumps() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 6, 8, 0, 0);

        assertEquals(1, count(path(world, new Cell(0, 1, 0), at(8, 1, 0)), MoveType.PARKOUR));
        assertTrue(noPath(world, new Cell(0, 1, 0), at(8, 1, 0), PathSettings.defaults().withLongJumps(false)));
    }

    @Test
    void rejectsAFourBlockGap() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 7, 9, 0, 0);

        assertTrue(noPath(world, new Cell(0, 1, 0), at(9, 1, 0)));
    }

    @Test
    void noJumpsWhenParkourIsOff() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 4, 6, 0, 0);

        assertTrue(noPath(world, new Cell(0, 1, 0), at(6, 1, 0), PathSettings.defaults().withParkour(false)));
    }

    @Test
    void jumpsUpOneBlockAcrossAOneOrTwoBlockGap() {
        FakeWorld oneGap = new FakeWorld().floor(0, 0, 2, 0, 0).floor(1, 4, 6, 0, 0);
        FakeWorld twoGap = new FakeWorld().floor(0, 0, 2, 0, 0).floor(1, 5, 7, 0, 0);

        assertEquals(1, count(path(oneGap, new Cell(0, 1, 0), at(6, 2, 0)), MoveType.PARKOUR));
        assertEquals(1, count(path(twoGap, new Cell(0, 1, 0), at(7, 2, 0)), MoveType.PARKOUR));
    }

    @Test
    void doesNotJumpUpAcrossAThreeBlockGap() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(1, 6, 8, 0, 0);

        assertTrue(noPath(world, new Cell(0, 1, 0), at(8, 2, 0)));
    }

    @Test
    void aLowCeilingStopsAJump() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 4, 6, 0, 0).set(3, 3, 0, Terrain.SOLID);

        assertTrue(noPath(world, new Cell(0, 1, 0), at(6, 1, 0)));
    }

    @Test
    void wontJumpOverLavaOrAnUnloadedGap() {
        FakeWorld lava = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 4, 6, 0, 0).set(3, 0, 0, Terrain.DANGER);
        FakeWorld unloaded = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 4, 6, 0, 0).set(3, 0, 0, Terrain.UNLOADED);

        assertTrue(noPath(lava, new Cell(0, 1, 0), at(6, 1, 0)));
        assertTrue(noPath(unloaded, new Cell(0, 1, 0), at(6, 1, 0)));
    }

    @Test
    void wontJumpWhenMomentumWouldCarryIntoLava() {
        // The landing is fine, but the cell just past it is lava, and a jump carries the body a little further.
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 4, 4, 0, 0).set(5, 1, 0, Terrain.DANGER);

        assertTrue(noPath(world, new Cell(0, 1, 0), at(4, 1, 0)));
    }

    @Test
    void doesNotJumpWhereItCouldWalk() {
        FakeWorld world = new FakeWorld().floor(0, 0, 8, 0, 0);

        assertEquals(0, count(path(world, new Cell(0, 1, 0), at(8, 1, 0)), MoveType.PARKOUR));
    }

    // ---------- diagonals ----------

    @Test
    void wontCutACornerPastAWall() {
        // A wall at (1, *, 0) blocks one corner of the diagonal step from (0,0) to (1,1).
        FakeWorld world = new FakeWorld().floor(0, 0, 3, 0, 3).wall(1, 1, 0, 0);

        assertTrue(!Moves.isPossible(world, new Cell(0, 1, 0), new Cell(1, 1, 1), PathSettings.defaults()));
        Path path = path(world, new Cell(0, 1, 0), at(2, 1, 1));
        PathSearchTest.assertLegal(world, path.cells());
    }

    @Test
    void wontCutACornerOverLava() {
        FakeWorld world = new FakeWorld().floor(0, 0, 3, 0, 3).set(1, 0, 0, Terrain.DANGER);

        assertTrue(!Moves.isPossible(world, new Cell(0, 1, 0), new Cell(1, 1, 1), PathSettings.defaults()));
    }

    // ---------- falls ----------

    @Test
    void dropsIntoWater() {
        FakeWorld world = new FakeWorld().floor(3, 0, 1, 0, 0).floor(0, 2, 2, 0, 0).set(2, 1, 0, Terrain.WATER);

        Path path = path(world, new Cell(1, 4, 0), at(2, 1, 0));

        assertEquals(new Cell(2, 1, 0), path.last());
        assertEquals(MoveType.DROP, path.waypoints().get(path.size() - 1).type());
    }

    @Test
    void fallsFarIntoWaterButNotOntoGround() {
        // A cliff 14 blocks above a two-deep pool. Too far onto stone, fine into water.
        FakeWorld water = new FakeWorld().floor(15, 0, 1, 0, 0).floor(0, 2, 4, 0, 0).fill(Terrain.WATER, 2, 2, 1, 2, 0, 0);
        FakeWorld stone = new FakeWorld().floor(15, 0, 1, 0, 0).floor(0, 2, 4, 0, 0);

        Path path = path(water, new Cell(0, 16, 0), at(2, 2, 0));
        assertEquals(new Cell(2, 2, 0), path.last());
        assertTrue(noPath(stone, new Cell(0, 16, 0), at(3, 1, 0)));
    }

    // ---------- swimming ----------

    /** Land at x 0..2 and 10..12, a lake between with its bed at y=0 and the surface at y=1 (air above). */
    private static FakeWorld lake() {
        return new FakeWorld().floor(0, 0, 12, 0, 0).fill(Terrain.WATER, 3, 9, 1, 1, 0, 0);
    }

    @Test
    void swimsAcrossALake() {
        Path path = path(lake(), new Cell(0, 1, 0), at(12, 1, 0));

        assertEquals(7, count(path, MoveType.SWIM));
        assertEquals(new Cell(12, 1, 0), path.last());
    }

    @Test
    void canGoToACellInTheWater() {
        assertEquals(new Cell(6, 1, 0), path(lake(), new Cell(0, 1, 0), at(6, 1, 0)).last());
    }

    @Test
    void climbsOutOfWaterOntoAHigherBank() {
        FakeWorld world = new FakeWorld().floor(0, 0, 4, 0, 0).fill(Terrain.WATER, 1, 3, 1, 1, 0, 0).floor(1, 4, 5, 0, 0);

        Path path = path(world, new Cell(0, 1, 0), at(5, 2, 0));

        assertEquals(new Cell(5, 2, 0), path.last());
        assertTrue(path.cells().contains(new Cell(4, 2, 0)), "should climb out onto the bank");
    }

    @Test
    void swimsDownAndUpAColumn() {
        FakeWorld world = new FakeWorld().floor(0, 0, 0, 0, 0).fill(Terrain.WATER, 0, 0, 1, 5, 0, 0);

        assertEquals(4, count(path(world, new Cell(0, 5, 0), new GoalYLevel(1)), MoveType.SWIM));
        assertEquals(4, count(path(world, new Cell(0, 1, 0), new GoalYLevel(5)), MoveType.SWIM));
    }

    @Test
    void refusesALongUnderwaterTunnelButAllowsAShortOne() {
        FakeWorld longTunnel = new FakeWorld().floor(0, 0, 40, 0, 0).fill(Terrain.WATER, 3, 27, 1, 2, 0, 0).floor(3, 3, 27, 0, 0);
        FakeWorld shortTunnel = new FakeWorld().floor(0, 0, 40, 0, 0).fill(Terrain.WATER, 3, 17, 1, 2, 0, 0).floor(3, 3, 17, 0, 0);

        assertTrue(noPath(longTunnel, new Cell(0, 1, 0), at(30, 1, 0)));
        assertEquals(new Cell(20, 1, 0), path(shortTunnel, new Cell(0, 1, 0), at(20, 1, 0)).last());
    }

    @Test
    void longSurfaceSwimsAreFine() {
        FakeWorld world = new FakeWorld().floor(0, 0, 60, 0, 0).fill(Terrain.WATER, 3, 50, 1, 1, 0, 0);

        assertEquals(new Cell(55, 1, 0), path(world, new Cell(0, 1, 0), at(55, 1, 0)).last());
    }

    // ---------- ladders ----------

    /** Ground x 0..3 at y=0, a ladder at x=1 from y=1 to y=5, and a platform at x 2..3 with its floor at y=5. */
    private static FakeWorld tower() {
        return new FakeWorld().floor(0, 0, 3, 0, 0).fill(Terrain.CLIMBABLE, 1, 1, 1, 5, 0, 0).floor(5, 2, 3, 0, 0);
    }

    @Test
    void climbsALadderToAPlatform() {
        Path path = path(tower(), new Cell(0, 1, 0), at(3, 6, 0));

        assertEquals(4, count(path, MoveType.LADDER));
        assertEquals(new Cell(3, 6, 0), path.last());
    }

    @Test
    void getsDownFromThePlatformByTheLadder() {
        Path path = path(tower(), new Cell(3, 6, 0), at(0, 1, 0));

        assertEquals(new Cell(0, 1, 0), path.last());
        PathSearchTest.assertLegal(tower(), path.cells());
    }

    @Test
    void cannotClimbThroughAClosedCeiling() {
        assertTrue(noPath(tower().set(1, 3, 0, Terrain.SOLID), new Cell(0, 1, 0), at(3, 6, 0)));
    }

    @Test
    void everyStepOfAMixedRouteIsALegalMove() {
        FakeWorld world = lake();
        Path path = path(world, new Cell(0, 1, 0), at(12, 1, 0));

        PathSearchTest.assertLegal(world, path.cells());
    }
}
