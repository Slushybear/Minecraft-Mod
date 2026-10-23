package com.slushybear.daedalus.pathfinding;

/**
 * Picks the cell a plan should start from. The raw block the feet are in is wrong in two common cases: standing on the
 * very edge of a block (the body hangs over empty air but is still held up by the block it overlaps), and mid-jump
 * (the feet are a block above where the player will land).
 */
public final class StartCell {
    /** The body is 0.6 wide, so its centre can be up to 0.8 from a block's centre while still standing on it. */
    private static final double SUPPORT_REACH = 0.8;

    private StartCell() {}

    /**
     * The block the feet are in. Soul sand and farmland tops sit below a full block, so a little is added before rounding
     * down, or standing on them would put the feet in the block itself.
     */
    public static Cell feetCell(double x, double y, double z) {
        return new Cell((int) Math.floor(x), (int) Math.floor(y + 0.1251), (int) Math.floor(z));
    }

    public static Cell choose(BlockView world, double x, double y, double z, boolean onGround) {
        Cell feet = feetCell(x, y, z);
        if (Moves.kind(world, feet) != Moves.Kind.NONE) {
            return feet;
        }
        if (onGround) {
            Cell best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Cell candidate = new Cell(feet.x() + dx, feet.y(), feet.z() + dz);
                    double offX = Math.abs(candidate.x() + 0.5 - x);
                    double offZ = Math.abs(candidate.z() + 0.5 - z);
                    if (offX > SUPPORT_REACH || offZ > SUPPORT_REACH || Moves.kind(world, candidate) != Moves.Kind.LAND) {
                        continue;
                    }
                    double distance = offX * offX + offZ * offZ;
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        } else {
            Cell below = new Cell(feet.x(), feet.y() - 1, feet.z());
            if (Moves.kind(world, below) == Moves.Kind.LAND) {
                return below; // mid-jump over ground we will land on
            }
        }
        return feet;
    }
}
