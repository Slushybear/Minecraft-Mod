package com.slushybear.daedalus.pathfinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Every single move the walker can make from a cell, with its cost in ticks. The search, the walker's live re-check and
 * the tests all use this one rule set, so a route is valid exactly when each step is one of these moves.
 *
 * <p>A cell is the block the feet are in. What the body can do there depends on its {@link Kind}.
 */
public final class Moves {
    public enum Kind {
        /** Not somewhere the body can be. */
        NONE,
        /** Feet and head in air, a full block below. */
        LAND,
        /** Feet in water, head in water or air. */
        SWIM,
        /** Feet in a climbable block, head in air or a climbable block. */
        LADDER
    }

    /** One step from a cell. `submerged` is the run of fully underwater steps after taking it. */
    public record Move(Cell to, double cost, MoveType type, int submerged) {}

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final double SQRT_2 = Math.sqrt(2);
    /** A ladder or vine only catches a fall this long or shorter. Past that the body is moving too fast to grab it. */
    private static final int MAX_LADDER_CATCH = 11;

    private Moves() {}

    public static Kind kind(BlockView world, Cell cell) {
        Terrain feet = world.terrainAt(cell.x(), cell.y(), cell.z());
        Terrain head = world.terrainAt(cell.x(), cell.y() + 1, cell.z());
        return switch (feet) {
            case AIR -> head == Terrain.AIR && world.terrainAt(cell.x(), cell.y() - 1, cell.z()) == Terrain.SOLID
                    ? Kind.LAND : Kind.NONE;
            case WATER -> head == Terrain.WATER || head == Terrain.AIR ? Kind.SWIM : Kind.NONE;
            case CLIMBABLE -> head == Terrain.AIR || head == Terrain.CLIMBABLE ? Kind.LADDER : Kind.NONE;
            default -> Kind.NONE;
        };
    }

    /** All moves from a cell. `submerged` is how long the body has already been fully underwater. */
    public static List<Move> from(BlockView world, Cell from, int submerged, PathSettings settings) {
        List<Move> moves = new ArrayList<>(28);
        Kind fromKind = kind(world, from);
        for (int[] side : SIDES) {
            addSideMoves(world, from, fromKind, side, submerged, settings, moves);
        }
        if (settings.allowDiagonal() && fromKind == Kind.LAND) {
            for (int[] diagonal : DIAGONALS) {
                addDiagonal(world, from, diagonal, settings, moves);
            }
        }
        addVerticalMoves(world, from, fromKind, submerged, settings, moves);
        if (settings.allowParkour() && fromKind == Kind.LAND) {
            for (int[] side : SIDES) {
                addParkour(world, from, side, settings, moves);
                if (settings.allowLongJumps()) {
                    addRunJump(world, from, side, moves);
                }
                addNeos(world, from, side, settings, moves);
                addSideJumps(world, from, side, settings, moves);
            }
        }
        return moves;
    }

    /** True if `to` is still one legal move away from `from`. The walker uses this to notice the world changing. */
    public static boolean isPossible(BlockView world, Cell from, Cell to, PathSettings settings) {
        for (Move move : from(world, from, 0, settings)) {
            if (move.to().equals(to)) {
                return true;
            }
        }
        return false;
    }

    private static void addSideMoves(BlockView world, Cell from, Kind fromKind, int[] side, int submerged,
                                     PathSettings settings, List<Move> moves) {
        int x = from.x() + side[0];
        int y = from.y();
        int z = from.z() + side[1];

        Cell level = new Cell(x, y, z);
        Kind levelKind = kind(world, level);
        switch (levelKind) {
            case LAND -> moves.add(new Move(level, flatCost(fromKind, settings) + dangerCost(world, level), MoveType.WALK, 0));
            case SWIM -> {
                if (settings.allowWater()) {
                    addWaterMove(world, level, waterCost(settings), MoveType.SWIM, submerged, moves);
                }
            }
            case LADDER -> {
                if (settings.allowLadders()) {
                    moves.add(new Move(level, Costs.WALK, MoveType.WALK, 0));
                }
            }
            default -> { }
        }

        // Step up one block. Needs room above the head to jump from here.
        Cell up = new Cell(x, y + 1, z);
        if (kind(world, up) == Kind.LAND && passable(world.terrainAt(from.x(), y + 2, from.z()))) {
            moves.add(new Move(up, Math.max(Costs.JUMP_UP, Costs.WALK) + Costs.JUMP_PENALTY + dangerCost(world, up),
                    MoveType.CLIMB, 0));
        }

        // Step from a platform onto the top of a ladder just below it.
        Cell ladderTop = new Cell(x, y - 1, z);
        if (settings.allowLadders() && kind(world, ladderTop) == Kind.LADDER && world.terrainAt(x, y + 1, z) == Terrain.AIR) {
            moves.add(new Move(ladderTop, Costs.WALK, MoveType.WALK, 0));
        }

        // Walk off the edge. Only when the level cell is open air with nothing to stand on.
        if (levelKind == Kind.NONE && world.terrainAt(x, y, z) == Terrain.AIR && world.terrainAt(x, y + 1, z) == Terrain.AIR) {
            addFall(world, from, x, z, submerged, settings, moves);
        }
    }

    /**
     * Falls straight down the column beside the start until something stops it: solid ground (up to maxFallNoWater),
     * water (up to maxFallIntoWater), or a ladder to grab. Anything else ends the search of this column.
     */
    private static void addFall(BlockView world, Cell from, int x, int z, int submerged, PathSettings settings, List<Move> moves) {
        int limit = Math.max(settings.maxFallNoWater() + 1, Math.max(settings.maxFallIntoWater(), MAX_LADDER_CATCH));
        for (int fall = 1; fall <= limit; fall++) {
            int y = from.y() - fall;
            Terrain terrain = world.terrainAt(x, y, z);
            switch (terrain) {
                case AIR -> {
                    continue;
                }
                case WATER -> {
                    Cell landing = new Cell(x, y, z);
                    if (settings.allowWater() && fall <= settings.maxFallIntoWater() && kind(world, landing) == Kind.SWIM) {
                        addWaterMove(world, landing, Costs.WALK_OFF_EDGE + Costs.fall(fall), MoveType.DROP, submerged, moves);
                    }
                }
                case CLIMBABLE -> {
                    Cell landing = new Cell(x, y, z);
                    if (settings.allowLadders() && fall <= MAX_LADDER_CATCH && kind(world, landing) == Kind.LADDER) {
                        moves.add(new Move(landing, Costs.WALK_OFF_EDGE + Costs.fall(fall), MoveType.DROP, 0));
                    }
                }
                case SOLID -> {
                    Cell landing = new Cell(x, y + 1, z);
                    int dropped = fall - 1;
                    if (dropped >= 1 && dropped <= settings.maxFallNoWater() && kind(world, landing) == Kind.LAND) {
                        double cost = Costs.WALK_OFF_EDGE + Math.max(Costs.fall(dropped), Costs.CENTER_AFTER_FALL);
                        moves.add(new Move(landing, cost + dangerCost(world, landing), MoveType.DROP, 0));
                    }
                }
                default -> { }
            }
            return;
        }
    }

    /**
     * A diagonal step between level cells. Both corner columns must be open at feet and head height, so the 0.6 wide
     * body never clips a block on the way past, and neither corner may have lava or fire underneath.
     */
    private static void addDiagonal(BlockView world, Cell from, int[] diagonal, PathSettings settings, List<Move> moves) {
        int x = from.x() + diagonal[0];
        int y = from.y();
        int z = from.z() + diagonal[1];
        Cell to = new Cell(x, y, z);
        if (kind(world, to) != Kind.LAND) {
            return;
        }
        if (!open(world, x, y, from.z()) || !open(world, from.x(), y, z)) {
            return;
        }
        if (world.terrainAt(x, y - 1, from.z()) == Terrain.DANGER || world.terrainAt(from.x(), y - 1, z) == Terrain.DANGER) {
            return;
        }
        double perBlock = settings.allowSprint() ? Costs.SPRINT : Costs.WALK;
        moves.add(new Move(to, perBlock * SQRT_2 + dangerCost(world, to), MoveType.DIAGONAL, 0));
    }

    private static void addVerticalMoves(BlockView world, Cell from, Kind fromKind, int submerged, PathSettings settings,
                                         List<Move> moves) {
        for (int dy : new int[]{1, -1}) {
            Cell to = new Cell(from.x(), from.y() + dy, from.z());
            Kind toKind = kind(world, to);
            if (fromKind == Kind.SWIM && toKind == Kind.SWIM && settings.allowWater()) {
                addWaterMove(world, to, waterCost(settings), MoveType.SWIM, submerged, moves);
            } else if (fromKind == Kind.LADDER && toKind == Kind.LADDER && settings.allowLadders()) {
                moves.add(new Move(to, dy > 0 ? Costs.LADDER_UP : Costs.LADDER_DOWN, MoveType.LADDER, 0));
            }
        }
    }

    /**
     * Jumps across a gap in a straight line. Gaps of 1 and 2 blocks are plain jumps. A 3-block gap needs a sprint-jump
     * and allowLongJumps. A landing one block higher is allowed over gaps of 1 or 2. Landings one or two blocks lower
     * are allowed over gaps of 1 and 2, and with allowLongJumps 3 and 4, since falling further carries the body further.
     * A level 4-block gap needs a run-up, which is {@link #addRunJump}.
     *
     * <p>A jump is only offered when there really is a gap: if the first cell has a floor, walking is the move. It needs
     * headroom at the takeoff and over the whole flight, nothing dangerous below the gap, and the two cells just past
     * the landing must be safe to stumble into, since momentum carries the body a little further.
     */
    private static void addParkour(BlockView world, Cell from, int[] side, PathSettings settings, List<Move> moves) {
        int x = from.x();
        int y = from.y();
        int z = from.z();
        int dx = side[0];
        int dz = side[1];
        if (world.terrainAt(x, y + 2, z) != Terrain.AIR) {
            return; // no room to jump
        }
        if (!airColumn(world, x + dx, y, z + dz)) {
            return;
        }
        Terrain firstFloor = world.terrainAt(x + dx, y - 1, z + dz);
        if (!isGapFloor(firstFloor)) {
            return; // solid means just walk; anything else under the gap is not safe to jump over
        }

        int maxDistance = settings.allowLongJumps() ? 5 : 3;
        for (int distance = 2; distance <= maxDistance; distance++) {
            int tx = x + dx * distance;
            int tz = z + dz * distance;
            if (world.terrainAt(tx, y + 1, tz) != Terrain.AIR || world.terrainAt(tx, y + 2, tz) != Terrain.AIR) {
                return;
            }

            if (world.terrainAt(tx, y, tz) != Terrain.AIR) {
                // Something to land on top of, one block up.
                Cell up = new Cell(tx, y + 1, tz);
                if (distance <= 3 && kind(world, up) == Kind.LAND && overshootSafe(world, tx + dx, y + 1, tz + dz)) {
                    moves.add(new Move(up, Costs.WALK * distance + Costs.JUMP_PENALTY + dangerCost(world, up), MoveType.PARKOUR, 0));
                }
                return;
            }

            Terrain floor = world.terrainAt(tx, y - 1, tz);
            if (floor == Terrain.SOLID) {
                Cell to = new Cell(tx, y, tz);
                if (distance <= 4 && kind(world, to) == Kind.LAND && overshootSafe(world, tx + dx, y, tz + dz)) {
                    moves.add(new Move(to, jumpRun(distance) + Costs.JUMP_PENALTY + dangerCost(world, to), MoveType.PARKOUR, 0));
                }
                return;
            }
            if (!isGapFloor(floor)) {
                return;
            }
            addJumpDown(world, from, side, distance, settings, moves);
            if (world.terrainAt(tx, y + 3, tz) != Terrain.AIR) {
                return; // the arc to a further landing rises into this cell's y+3
            }
        }
    }

    /**
     * A landing one or two blocks below the takeoff, in the column `distance` cells along. The column just before it must
     * be open down to the landing's height, because the body is already coming down as it reaches the landing.
     */
    private static void addJumpDown(BlockView world, Cell from, int[] side, int distance, PathSettings settings, List<Move> moves) {
        int tx = from.x() + side[0] * distance;
        int tz = from.z() + side[1] * distance;
        int px = tx - side[0];
        int pz = tz - side[1];
        int maxDrop = Math.min(2, settings.maxFallNoWater());
        for (int drop = 1; drop <= maxDrop; drop++) {
            int y = from.y() - drop;
            if (world.terrainAt(tx, y, tz) != Terrain.AIR || !isGapFloor(world.terrainAt(px, y, pz))) {
                return;
            }
            if (world.terrainAt(tx, y - 1, tz) == Terrain.SOLID) {
                Cell to = new Cell(tx, y, tz);
                if (overshootSafe(world, tx + side[0], y, tz + side[1])) {
                    double cost = jumpRun(distance) + Costs.JUMP_PENALTY + Costs.fall(drop) + dangerCost(world, to);
                    moves.add(new Move(to, cost, MoveType.PARKOUR, 0));
                }
                return;
            }
        }
    }

    /**
     * A level 4-block gap. From a standstill on the takeoff block the body is too slow by the edge, so this move starts
     * one block back: from `from` it sprints across the takeoff block, jumps, and lands 6 blocks along. It has little
     * margin for error, so it costs extra and is only chosen when it saves real time.
     */
    private static void addRunJump(BlockView world, Cell from, int[] side, List<Move> moves) {
        int dx = side[0];
        int dz = side[1];
        Cell takeoff = new Cell(from.x() + dx, from.y(), from.z() + dz);
        int y = from.y();
        if (kind(world, takeoff) != Kind.LAND || world.terrainAt(takeoff.x(), y + 2, takeoff.z()) != Terrain.AIR) {
            return;
        }
        for (int k = 1; k <= 4; k++) {
            int gx = takeoff.x() + dx * k;
            int gz = takeoff.z() + dz * k;
            if (!airColumn(world, gx, y, gz) || world.terrainAt(gx, y + 3, gz) != Terrain.AIR
                    || !isGapFloor(world.terrainAt(gx, y - 1, gz))) {
                return;
            }
        }
        Cell to = new Cell(takeoff.x() + dx * 5, y, takeoff.z() + dz * 5);
        if (kind(world, to) != Kind.LAND || world.terrainAt(to.x(), y + 2, to.z()) != Terrain.AIR
                || !overshootSafe(world, to.x() + dx, y, to.z() + dz)) {
            return;
        }
        double cost = Costs.SPRINT * 6 + Costs.JUMP_PENALTY + Costs.RISKY_JUMP_PENALTY + dangerCost(world, to);
        moves.add(new Move(to, cost, MoveType.RUN_JUMP, 0));
    }

    /**
     * Neos: a level landing straight ahead across a gap of 1 to 3, with something standing in the straight line, so the
     * body has to curve out around it and back in. Only tried where one side of the obstacle is open gap all along,
     * since anywhere with a floor beside it walking round is the better move. {@link JumpSim} decides if it lands.
     */
    private static void addNeos(BlockView world, Cell from, int[] side, PathSettings settings, List<Move> moves) {
        int x = from.x();
        int y = from.y();
        int z = from.z();
        int dx = side[0];
        int dz = side[1];
        if (world.terrainAt(x, y + 2, z) != Terrain.AIR) {
            return;
        }
        int maxDistance = settings.allowLongJumps() ? 4 : 3;
        for (int distance = 2; distance <= maxDistance; distance++) {
            Cell to = new Cell(x + dx * distance, y, z + dz * distance);
            if (kind(world, to) != Kind.LAND || !overshootSafe(world, to.x() + dx, y, to.z() + dz)) {
                continue;
            }
            boolean blocked = false;
            boolean danger = false;
            for (int k = 1; k < distance; k++) {
                blocked |= JumpSim.laneBlocked(world, from, dx, dz, k, 0);
                for (int h = y; h <= y + 2; h++) {
                    danger |= world.terrainAt(x + dx * k, h, z + dz * k) == Terrain.DANGER;
                }
            }
            // Never curve past lava or fire: a little drift in the air would be the end of it.
            if (!blocked || danger || !(openSideLane(world, from, dx, dz, distance, 1) || openSideLane(world, from, dx, dz, distance, -1))) {
                continue;
            }
            if (JumpSim.neo(world, from, to) != null) {
                double cost = Costs.SPRINT * (distance + 1) + Costs.JUMP_PENALTY + Costs.RISKY_JUMP_PENALTY + dangerCost(world, to);
                moves.add(new Move(to, cost, MoveType.NEO, 0));
            }
        }
    }

    /** True if the lane beside the jump, on side j, is open air over a gap for the whole length of the jump. */
    private static boolean openSideLane(BlockView world, Cell from, int dx, int dz, int distance, int j) {
        for (int k = 1; k < distance; k++) {
            if (JumpSim.laneBlocked(world, from, dx, dz, k, j)) {
                return false;
            }
            int x = from.x() + dx * k - dz * j;
            int z = from.z() + dz * k + dx * j;
            if (!isGapFloor(world.terrainAt(x, from.y() - 1, z))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Side jumps: a landing 2 to 4 blocks ahead and one to the side, level, one lower, or one higher for 2 or 3 ahead.
     * Only tried when none of the three cells next to the takeoff in that direction can be walked to, so it is never a
     * fancy way to do what walking does. {@link JumpSim} decides if it lands.
     */
    private static void addSideJumps(BlockView world, Cell from, int[] side, PathSettings settings, List<Move> moves) {
        int x = from.x();
        int y = from.y();
        int z = from.z();
        int dx = side[0];
        int dz = side[1];
        if (world.terrainAt(x, y + 2, z) != Terrain.AIR || kind(world, new Cell(x + dx, y, z + dz)) == Kind.LAND) {
            return;
        }
        int maxDistance = settings.allowLongJumps() ? 4 : 3;
        for (int j = -1; j <= 1; j += 2) {
            int px = -dz * j;
            int pz = dx * j;
            if (kind(world, new Cell(x + px, y, z + pz)) == Kind.LAND || kind(world, new Cell(x + dx + px, y, z + dz + pz)) == Kind.LAND) {
                continue;
            }
            for (int distance = 2; distance <= maxDistance; distance++) {
                for (int rise = -1; rise <= (distance <= 3 ? 1 : 0); rise++) {
                    Cell to = new Cell(x + dx * distance + px, y + rise, z + dz * distance + pz);
                    if (kind(world, to) != Kind.LAND || !overshootSafe(world, to.x() + dx, to.y(), to.z() + dz)) {
                        continue;
                    }
                    if (JumpSim.side(world, from, to) != null) {
                        double height = rise > 0 ? Costs.JUMP_UP : rise < 0 ? Costs.fall(1) : 0;
                        double cost = jumpRun(distance) + Costs.SPRINT + Costs.JUMP_PENALTY + height + dangerCost(world, to);
                        moves.add(new Move(to, cost, MoveType.SIDE_JUMP, 0));
                    }
                }
            }
        }
    }

    /** Time to cover a jump's distance: plain jumps at walking pace, sprint-jumps at sprinting pace. */
    private static double jumpRun(int distance) {
        return distance < 4 ? Costs.WALK * distance : Costs.SPRINT * distance;
    }

    private static void addWaterMove(BlockView world, Cell to, double cost, MoveType type, int submerged, List<Move> moves) {
        int count = world.terrainAt(to.x(), to.y() + 1, to.z()) == Terrain.WATER ? submerged + 1 : 0;
        if (count <= Costs.MAX_SUBMERGED) {
            moves.add(new Move(to, cost, type, count));
        }
    }

    private static double flatCost(Kind fromKind, PathSettings settings) {
        return settings.allowSprint() && fromKind == Kind.LAND ? Costs.SPRINT : Costs.WALK;
    }

    private static double waterCost(PathSettings settings) {
        return settings.allowSprint() ? Costs.SWIM : Costs.WADE;
    }

    private static boolean open(BlockView world, int x, int y, int z) {
        return world.terrainAt(x, y, z) == Terrain.AIR && world.terrainAt(x, y + 1, z) == Terrain.AIR;
    }

    private static boolean airColumn(BlockView world, int x, int y, int z) {
        return world.terrainAt(x, y, z) == Terrain.AIR
                && world.terrainAt(x, y + 1, z) == Terrain.AIR
                && world.terrainAt(x, y + 2, z) == Terrain.AIR;
    }

    private static boolean isGapFloor(Terrain terrain) {
        return terrain == Terrain.AIR || terrain == Terrain.WATER;
    }

    private static boolean overshootSafe(BlockView world, int x, int y, int z) {
        Terrain feet = world.terrainAt(x, y, z);
        Terrain head = world.terrainAt(x, y + 1, z);
        return feet != Terrain.DANGER && feet != Terrain.UNLOADED && head != Terrain.DANGER && head != Terrain.UNLOADED;
    }

    private static boolean passable(Terrain terrain) {
        return terrain == Terrain.AIR || terrain == Terrain.WATER || terrain == Terrain.CLIMBABLE;
    }

    private static double dangerCost(BlockView world, Cell cell) {
        return nearDanger(world, cell) ? Costs.NEAR_DANGER : 0;
    }

    /** True if lava or fire touches the sides of this cell, at foot or floor level. */
    static boolean nearDanger(BlockView world, Cell cell) {
        for (int[] side : SIDES) {
            for (int y = cell.y() - 1; y <= cell.y(); y++) {
                if (world.terrainAt(cell.x() + side[0], y, cell.z() + side[1]) == Terrain.DANGER) {
                    return true;
                }
            }
        }
        return false;
    }
}
