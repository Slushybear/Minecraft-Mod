package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Costs that follow the player's speed: which route, which gait, and which jumps are worth planning. */
class SpeedProfileTest {
    private static final int CAP = 20000;

    private static Path route(BlockView world, Cell start, Cell goal, PathSettings settings) {
        return PathSearch.findPath(world, start, new GoalBlock(goal, 0), settings.withMaxNodes(CAP), Set.of()).orElseThrow();
    }

    private static long count(Path path, MoveType type) {
        return path.waypoints().stream().filter(w -> w.type() == type).count();
    }

    @Test
    void vanillaSpeedMatchesTheKnownGaits() {
        SpeedProfile vanilla = SpeedProfile.vanilla();

        assertEquals(20 / 4.317, vanilla.walk(), 0.001);
        assertEquals(20 / 5.612, vanilla.sprint(), 0.001);
    }

    @Test
    void groundCostsScaleWithSpeed() {
        SpeedProfile fast = new SpeedProfile(4);

        assertEquals(SpeedProfile.vanilla().walk() / 4, fast.walk(), 1e-9);
        assertEquals(SpeedProfile.vanilla().sprint() / 4, fast.sprint(), 1e-9);
    }

    @Test
    void sprintJumpingBeatsSprintingAtVanillaSpeedAndNotAtHighSpeed() {
        assertTrue(SpeedProfile.vanilla().hops());
        assertTrue(SpeedProfile.vanilla().hop() < SpeedProfile.vanilla().sprint() * 0.9, "hopping should be clearly faster");
        assertTrue(new SpeedProfile(0.6).hops());
        assertFalse(new SpeedProfile(1.5).hops());
        assertFalse(new SpeedProfile(4).hops());
        assertFalse(new SpeedProfile(10).hops());
    }

    @Test
    void hopSpeedIsAboutWhatVanillaPlayersSee() {
        // Players report roughly 7 blocks a second for a chain of sprint-jumps.
        double blocksPerSecond = 20 / SpeedProfile.vanilla().hop();

        assertTrue(blocksPerSecond > 6.3 && blocksPerSecond < 7.6, "was " + blocksPerSecond);
    }

    @Test
    void anOpenFlatRunCostsLessHoppingThanSprinting() {
        FakeWorld open = new FakeWorld().floor(0, 0, 20, 0, 0);
        FakeWorld lowCeiling = new FakeWorld().floor(0, 0, 20, 0, 0).fill(Terrain.SOLID, 0, 20, 3, 3, 0, 0);

        double hopping = route(open, new Cell(0, 1, 0), new Cell(20, 1, 0), PathSettings.defaults()).cost();
        double sprinting = route(lowCeiling, new Cell(0, 1, 0), new Cell(20, 1, 0), PathSettings.defaults()).cost();

        assertTrue(hopping < sprinting * 0.9, "hopping " + hopping + " vs sprinting " + sprinting);
        assertEquals(20 * SpeedProfile.vanilla().sprint(), sprinting, 0.5);
    }

    @Test
    void aFastPlayerPaysLessForTheSameFlatRun() {
        FakeWorld world = new FakeWorld().floor(0, 0, 30, 0, 0);
        PathSettings slow = PathSettings.defaults().withSpeed(1.5);
        PathSettings fast = PathSettings.defaults().withSpeed(5);

        double slowCost = route(world, new Cell(0, 1, 0), new Cell(30, 1, 0), slow).cost();
        double fastCost = route(world, new Cell(0, 1, 0), new Cell(30, 1, 0), fast).cost();

        assertEquals(slowCost * 1.5 / 5, fastCost, 0.05);
    }

    @Test
    void fixedCostsDoNotShrinkWithSpeed() {
        // A 3-block drop takes the same time to fall however fast the body walks to the edge.
        FakeWorld world = new FakeWorld().floor(0, 0, 4, 0, 0).floor(-3, 5, 8, 0, 0);

        double vanilla = route(world, new Cell(4, 1, 0), new Cell(5, -2, 0), PathSettings.defaults()).cost();
        double fast = route(world, new Cell(4, 1, 0), new Cell(5, -2, 0), PathSettings.defaults().withSpeed(5)).cost();

        assertTrue(fast > vanilla * 0.6, "a drop must not get 5x cheaper: " + vanilla + " vs " + fast);
        assertTrue(fast >= Costs.fall(3), "never less than the time to fall");
    }

    @Test
    void aFastPlayerWalksRoundAStepInsteadOfClimbingIt() {
        // A single raised block sits in a 3-wide lane. Round it costs about a block of extra walking, over it costs a jump.
        FakeWorld world = new FakeWorld().floor(0, 0, 12, -1, 1).floor(1, 6, 6, 0, 0);
        PathSettings fast = PathSettings.defaults().withSpeed(5);

        Path path = route(world, new Cell(0, 1, 0), new Cell(12, 1, 0), fast);

        assertEquals(0, count(path, MoveType.CLIMB), "should go round, not climb");
    }

    @Test
    void jumpsAreOnlyPlannedWhereTheirPhysicsIsTrusted() {
        PathSettings vanilla = PathSettings.defaults();

        assertTrue(vanilla.allowParkour() && vanilla.allowLongJumps());
        assertTrue(vanilla.withSpeed(1.1).allowLongJumps());
        assertFalse(vanilla.withSpeed(1.5).allowLongJumps(), "sprint-jump reach changes with speed");
        assertTrue(vanilla.withSpeed(1.5).allowParkour(), "plain jumps still work, the walker slows down for them");
        assertTrue(vanilla.withSpeed(5).allowParkour());
        assertFalse(vanilla.withSpeed(0.5).allowParkour(), "too slow for the jump model");
    }

    @Test
    void jumpingUpABlockNeedsTheSprintBoostSoItIsNotPlannedForAFastPlayer() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(1, 4, 6, 0, 0);

        assertEquals(1, count(route(world, new Cell(0, 1, 0), new Cell(6, 2, 0), PathSettings.defaults()), MoveType.PARKOUR));
        assertTrue(PathSearch.findPath(world, new Cell(0, 1, 0), new GoalBlock(new Cell(6, 2, 0), 0),
                PathSettings.defaults().withSpeed(3).withMaxNodes(CAP), Set.of()).isEmpty());
    }

    @Test
    void theWalkerRunsUpToAJumpAtVanillaPace() {
        assertEquals(1, PathSettings.defaults().jumpThrottle(), 1e-9);
        assertEquals(0.25, PathSettings.defaults().withSpeed(4).jumpThrottle(), 1e-9);
        assertEquals(1, PathSettings.defaults().withSpeed(0.9).jumpThrottle(), 1e-9);
    }

    @Test
    void heuristicsStayLowerBoundsAtEverySpeed() {
        FakeWorld world = new FakeWorld().floor(0, 0, 25, 0, 6).floor(1, 8, 8, 0, 6);
        for (double factor : new double[]{0.6, 1, 1.5, 3, 5, 10}) {
            PathSettings settings = PathSettings.defaults().withSpeed(factor);
            Goal goal = new GoalBlock(new Cell(25, 1, 6), 0);
            Costs.useMinFlat(settings.fastestFlat());
            double estimate;
            try {
                estimate = goal.heuristic(new Cell(0, 1, 0));
            } finally {
                Costs.resetMinFlat();
            }

            double actual = route(world, new Cell(0, 1, 0), new Cell(25, 1, 6), settings).cost();

            assertTrue(estimate <= actual + 1e-6, "speed " + factor + ": estimate " + estimate + " > real " + actual);
        }
    }

    @Test
    void hoppingIsOnlyAssumedWhereThereIsRoomOverhead() {
        FakeWorld world = new FakeWorld().floor(0, 0, 4, 0, 0).set(2, 4, 0, Terrain.SOLID);

        assertTrue(Moves.hopRoom(world, new Cell(0, 1, 0)));
        assertFalse(Moves.hopRoom(world, new Cell(2, 1, 0)));
    }
}
