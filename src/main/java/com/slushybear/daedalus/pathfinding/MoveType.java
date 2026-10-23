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
    /** Jump across a gap of 1 to 3 blocks, landing level or one block higher. */
    PARKOUR
}
