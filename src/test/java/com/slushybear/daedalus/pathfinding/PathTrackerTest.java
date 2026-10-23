package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PathTrackerTest {
    private static Path route(BlockView world, Cell start, Cell goal) {
        return PathSearch.findPath(world, start, new GoalBlock(goal, 0), 20000, Set.of()).orElseThrow();
    }

    /** A straight 1-wide corridor, so the route is a plain line of WALK moves. */
    private static Path corridor() {
        return route(new FakeWorld().floor(0, 0, 10, 0, 0), new Cell(0, 1, 0), new Cell(10, 1, 0));
    }

    private static PathTracker.Status standAt(PathTracker tracker, double x, double z) {
        return tracker.update(x, 1, z, true);
    }

    @Test
    void followsAStraightRouteToTheEnd() {
        PathTracker tracker = new PathTracker(corridor());

        for (int x = 0; x <= 9; x++) {
            assertEquals(PathTracker.Status.ON_TRACK, standAt(tracker, x + 0.5, 0.5), "at x=" + x);
        }
        assertEquals(PathTracker.Status.FINISHED, standAt(tracker, 10.5, 0.5));
    }

    @Test
    void movesOnAsSoonAsTheFeetEnterTheNextCell() {
        PathTracker tracker = new PathTracker(corridor());

        standAt(tracker, 1.05, 0.5);

        assertEquals(1, tracker.moveIndex());
        assertEquals(new Cell(2, 1, 0), tracker.to().cell());
    }

    @Test
    void aheadStartsAfterTheCurrentTarget() {
        PathTracker tracker = new PathTracker(corridor());

        assertEquals(new Cell(1, 1, 0), tracker.to().cell());
        assertEquals(new Cell(2, 1, 0), tracker.ahead(0).cell());
        assertEquals(new Cell(3, 1, 0), tracker.ahead(1).cell());
    }

    /** The walker re-checks each move ahead with Moves.isPossible, so every planned move must pass it. */
    @Test
    void everyMoveAheadIsPossibleOnAnUnchangedWorld() {
        FakeWorld world = new FakeWorld().floor(0, 0, 10, 0, 0);
        PathTracker tracker = new PathTracker(route(world, new Cell(0, 1, 0), new Cell(10, 1, 0)));

        Cell from = tracker.to().cell();
        for (int k = 0; tracker.ahead(k) != null; k++) {
            Cell next = tracker.ahead(k).cell();
            assertTrue(Moves.isPossible(world, from, next, PathSettings.defaults()), from + " -> " + next);
            from = next;
        }
    }

    @Test
    void jumpsBackAfterKnockback() {
        PathTracker tracker = new PathTracker(corridor());
        for (int x = 0; x <= 6; x++) {
            standAt(tracker, x + 0.5, 0.5);
        }

        assertEquals(PathTracker.Status.ON_TRACK, standAt(tracker, 2.5, 0.5));

        assertTrue(tracker.moveIndex() <= 2, "should be back on the move that covers x=2, was " + tracker.moveIndex());
        assertEquals(1, tracker.resyncs());
    }

    @Test
    void jumpsForwardWhenAhead() {
        PathTracker tracker = new PathTracker(corridor());

        assertEquals(PathTracker.Status.ON_TRACK, standAt(tracker, 6.5, 0.5));

        assertTrue(tracker.moveIndex() >= 5);
    }

    @Test
    void driftingIsNotLostStraightAway() {
        PathTracker tracker = new PathTracker(corridor());
        standAt(tracker, 0.5, 0.5);

        // 2.5 blocks to the side of a route that runs along z=0.
        for (int tick = 0; tick < 50; tick++) {
            assertEquals(PathTracker.Status.ON_TRACK, standAt(tracker, 0.5, 3.0), "tick " + tick);
        }
    }

    @Test
    void driftingForTooLongIsOffPath() {
        // One long move (a slow one, like breaking through blocks would be), so the move timeout does not fire first.
        Path slow = new Path(java.util.List.of(
                new Waypoint(new Cell(0, 1, 0), MoveType.WALK, 0),
                new Waypoint(new Cell(1, 1, 0), MoveType.WALK, 10_000)));
        PathTracker tracker = new PathTracker(slow);

        for (int tick = 0; tick < PathTracker.MAX_DRIFT_TICKS; tick++) {
            assertEquals(PathTracker.Status.ON_TRACK, standAt(tracker, 0.5, 3.0), "tick " + tick);
        }
        assertEquals(PathTracker.Status.OFF_PATH, standAt(tracker, 0.5, 3.0));
    }

    @Test
    void farFromTheRouteIsLostAtOnce() {
        PathTracker tracker = new PathTracker(corridor());

        assertEquals(PathTracker.Status.OFF_PATH, standAt(tracker, 0.5, 4.5));
    }

    @Test
    void aMoveThatTakesFarTooLongTimesOut() {
        Path path = corridor();
        PathTracker tracker = new PathTracker(path);
        // The move may run while its tick count is at most its expected cost plus the grace.
        int limit = (int) Math.floor(path.waypoints().get(1).cost() + PathTracker.TIMEOUT_GRACE_TICKS);

        for (int tick = 0; tick < limit; tick++) {
            assertEquals(PathTracker.Status.ON_TRACK, standAt(tracker, 0.5, 0.5));
        }
        assertEquals(PathTracker.Status.TIMED_OUT, standAt(tracker, 0.5, 0.5));
    }

    @Test
    void fallingIsNotOffCourse() {
        // Walk off a 3-high ledge: mid-fall the player is far from the cells of the ledge and the landing.
        FakeWorld world = new FakeWorld().floor(3, 0, 1, 0, 0).floor(0, 2, 5, 0, 0);
        PathTracker tracker = new PathTracker(route(world, new Cell(1, 4, 0), new Cell(5, 1, 0)));

        assertEquals(PathTracker.Status.ON_TRACK, tracker.update(1.5, 4, 0.5, true));
        assertEquals(PathTracker.Status.ON_TRACK, tracker.update(2.4, 2.6, 0.5, false));
        assertEquals(PathTracker.Status.ON_TRACK, tracker.update(2.5, 1.0, 0.5, true));
    }

    @Test
    void theArcOfAJumpIsPartOfTheRoute() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 5, 7, 0, 0);
        Path path = route(world, new Cell(0, 1, 0), new Cell(7, 1, 0));
        PathTracker tracker = new PathTracker(path);
        tracker.update(0.5, 1, 0.5, true);
        tracker.update(1.5, 1, 0.5, true);
        tracker.update(2.5, 1, 0.5, true);

        // Over the gap, at the top of the jump.
        assertEquals(PathTracker.Status.ON_TRACK, tracker.update(3.6, 2.2, 0.5, false));
        assertEquals(MoveType.PARKOUR, tracker.to().type());
    }

    @Test
    void cancellingIsOnlySafeOnTheGroundAndBeforeAJump() {
        FakeWorld world = new FakeWorld().floor(0, 0, 2, 0, 0).floor(0, 5, 7, 0, 0);
        PathTracker tracker = new PathTracker(route(world, new Cell(0, 1, 0), new Cell(7, 1, 0)));
        tracker.update(0.5, 1, 0.5, true);
        tracker.update(1.5, 1, 0.5, true);
        tracker.update(2.5, 1, 0.5, true);

        assertTrue(tracker.safeToCancel(2.5, 1, 0.5, true), "standing on the takeoff block");
        assertFalse(tracker.safeToCancel(3.2, 1.8, 0.5, false), "in the air");
        assertFalse(tracker.safeToCancel(3.1, 1.0, 0.5, true), "already past the edge, committed to the jump");
    }

    @Test
    void diagonalCornersArePartOfTheRoute() {
        Waypoint diagonal = new Waypoint(new Cell(1, 1, 1), MoveType.DIAGONAL, 5);

        Set<Cell> cells = PathTracker.validCellsFor(new Cell(0, 1, 0), diagonal);

        assertTrue(cells.contains(new Cell(1, 1, 0)) && cells.contains(new Cell(0, 1, 1)));
    }
}
