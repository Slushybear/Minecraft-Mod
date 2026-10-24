package com.slushybear.daedalus.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Routes that change the way of moving part-way: walking into water and climbing out, getting on and off a ladder, jumps
 * and drops between walks. Each route must also pass the walker's own re-check, move by move, at every speed: a move
 * the search plans but the walker would reject stops the walk dead on its first step.
 */
class TransitionsTest {
    private static final int CAP = 30000;
    private static final double[] SPEEDS = {0.9, 1, 1.5, 4};

    private static Path route(BlockView world, Cell start, Cell goal, PathSettings settings) {
        return PathSearch.findPath(world, start, new GoalBlock(goal, 0), settings.withMaxNodes(CAP), Set.of())
                .orElseThrow(() -> new AssertionError("no route at " + settings.profile().factor() + "x"));
    }

    private static List<MoveType> types(Path path) {
        return path.waypoints().stream().skip(1).map(Waypoint::type).toList();
    }

    private static void assertWalkerAccepts(BlockView world, Path path, PathSettings settings) {
        List<Waypoint> waypoints = path.waypoints();
        for (int i = 1; i < waypoints.size(); i++) {
            Cell from = waypoints.get(i - 1).cell();
            Cell to = waypoints.get(i).cell();
            assertTrue(Moves.isPossible(world, from, to, settings),
                    "at " + settings.profile().factor() + "x the walker would reject " + from + " -> " + to + " "
                            + waypoints.get(i).type() + ": " + Moves.explain(world, from, to));
        }
    }

    /** Land, a lake two deep with nothing to walk round on, then land again a block higher than the water. */
    private static FakeWorld lake() {
        return new FakeWorld()
                .floor(0, 0, 2, 0, 0)
                .floor(-2, 3, 7, 0, 0)
                .fill(Terrain.WATER, 3, 7, -1, 0, 0, 0)
                .floor(0, 8, 11, 0, 0);
    }

    @Test
    void dropsIntoALakeSwimsAcrossAndClimbsOut() {
        for (double speed : SPEEDS) {
            PathSettings settings = PathSettings.defaults().withSpeed(speed);
            FakeWorld world = lake();

            Path path = route(world, new Cell(0, 1, 0), new Cell(11, 1, 0), settings);
            List<MoveType> types = types(path);

            assertTrue(types.contains(MoveType.SWIM), speed + "x: " + types);
            assertTrue(types.contains(MoveType.DROP), speed + "x: should drop in: " + types);
            assertTrue(types.contains(MoveType.CLIMB), speed + "x: should climb out: " + types);
            int climb = types.indexOf(MoveType.CLIMB);
            assertTrue(types.subList(0, climb).contains(MoveType.SWIM), speed + "x: climbs out after swimming: " + types);
            assertWalkerAccepts(world, path, settings);
        }
    }

    /** A ladder up a five-high wall, with a platform on top. */
    private static FakeWorld ladderWall() {
        return new FakeWorld()
                .floor(0, 0, 3, 0, 0)
                .fill(Terrain.CLIMBABLE, 3, 3, 1, 5, 0, 0)
                .fill(Terrain.SOLID, 4, 4, 1, 5, 0, 0)
                .floor(5, 5, 7, 0, 0);
    }

    @Test
    void climbsALadderAndStepsOffAtTheTop() {
        for (double speed : SPEEDS) {
            PathSettings settings = PathSettings.defaults().withSpeed(speed);
            FakeWorld world = ladderWall();

            Path path = route(world, new Cell(0, 1, 0), new Cell(7, 6, 0), settings);
            List<MoveType> types = types(path);

            assertEquals(4, types.stream().filter(t -> t == MoveType.LADDER).count(), speed + "x: " + types);
            int firstLadder = types.indexOf(MoveType.LADDER);
            int lastLadder = types.lastIndexOf(MoveType.LADDER);
            assertEquals(MoveType.WALK, types.get(firstLadder - 1), "walks onto the ladder: " + types);
            assertEquals(MoveType.CLIMB, types.get(lastLadder + 1), "climbs off the top onto the platform: " + types);
            assertWalkerAccepts(world, path, settings);
        }
    }

    @Test
    void comesBackDownTheLadder() {
        for (double speed : SPEEDS) {
            PathSettings settings = PathSettings.defaults().withSpeed(speed);
            FakeWorld world = ladderWall();

            Path path = route(world, new Cell(6, 6, 0), new Cell(0, 1, 0), settings);
            List<MoveType> types = types(path);

            assertTrue(types.contains(MoveType.LADDER), speed + "x: " + types);
            assertWalkerAccepts(world, path, settings);
        }
    }

    /** Steps up, a gap, a drop and a walk, in one lane. */
    private static FakeWorld obstacleCourse() {
        return new FakeWorld()
                .floor(0, 0, 3, 0, 0)
                .floor(1, 4, 6, 0, 0)
                .floor(1, 9, 11, 0, 0)
                .floor(-1, 12, 16, 0, 0);
    }

    @Test
    void chainsStepsJumpsAndDrops() {
        for (double speed : SPEEDS) {
            PathSettings settings = PathSettings.defaults().withSpeed(speed);
            FakeWorld world = obstacleCourse();

            Path path = route(world, new Cell(0, 1, 0), new Cell(16, 0, 0), settings);
            List<MoveType> types = types(path);

            assertTrue(types.contains(MoveType.CLIMB), speed + "x: steps up: " + types);
            assertTrue(types.contains(MoveType.PARKOUR), speed + "x: jumps the gap: " + types);
            assertTrue(goesDown(path), speed + "x: gets down to the low floor by a drop or a jump down: " + types);
            assertWalkerAccepts(world, path, settings);
        }
    }

    /** True if some move ends lower than it started: a drop, or a jump that lands lower (either is a fine way down). */
    private static boolean goesDown(Path path) {
        List<Waypoint> waypoints = path.waypoints();
        for (int i = 1; i < waypoints.size(); i++) {
            MoveType type = waypoints.get(i).type();
            boolean lower = waypoints.get(i).cell().y() < waypoints.get(i - 1).cell().y();
            if (lower && (type == MoveType.DROP || type == MoveType.PARKOUR)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void walkerAcceptsEveryRouteInAMixedWorld() {
        FakeWorld world = new FakeWorld()
                .floor(0, 0, 20, -4, 4)
                .fill(Terrain.SOLID, 6, 6, 1, 2, -4, 2)
                .fill(Terrain.WATER, 10, 13, 0, 0, -4, 4)
                .floor(-1, 10, 13, -4, 4)
                .floor(1, 16, 18, -4, 4)
                .set(15, 1, 0, Terrain.DANGER);
        for (double speed : SPEEDS) {
            PathSettings settings = PathSettings.defaults().withSpeed(speed);
            for (int z = -4; z <= 4; z += 4) {
                Path path = route(world, new Cell(0, 1, z), new Cell(20, 1, -z), settings);
                assertWalkerAccepts(world, path, settings);
            }
        }
    }
}
