package com.slushybear.daedalus.pathfinding;

/** How the walker reaches a cell. The executor needs this to choose which keys to press. */
public enum MoveType {
    /** Step to a level neighbour, or onto a ladder beside you. */
    WALK,
    /** Step diagonally to a level neighbour, with both corners clear. */
    DIAGONAL,
    /** Jump up one block. */
    CLIMB,
    /** Walk off an edge and fall onto ground below, or into water. */
    DROP,
    /** Move through water, sideways or straight up or down. */
    SWIM,
    /** Move up or down a ladder or vine. */
    LADDER,
    /**
     * Jump across a gap from where you stand: 1 to 3 blocks landing level, 1 or 2 blocks landing one higher, or 1 to 4
     * blocks landing one or two lower.
     */
    PARKOUR,
    /**
     * Sprint across one block of run-up, then jump a 4-block gap to a level landing. The move starts on the run-up
     * block, since a standing jump from the takeoff block is too slow to make it.
     */
    RUN_JUMP,
    /** Jump to a landing straight ahead, curving out around a wall in the way and back in. Steered by {@link JumpSim}. */
    NEO,
    /** Jump to a landing one block off to the side of the jump's main direction. Steered by {@link JumpSim}. */
    SIDE_JUMP
}
