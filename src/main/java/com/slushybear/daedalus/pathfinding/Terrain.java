package com.slushybear.daedalus.pathfinding;

/** What the pathfinder sees in one block cell. No Minecraft types, so the search can be tested on its own. */
public enum Terrain {
    /** Empty space a body can occupy. */
    AIR,
    /** A block a body can stand on or must not pass through. */
    SOLID,
    /** Water. Not walkable in this version. */
    WATER,
    /** Lava, fire and similar. Never walked through or stood on. */
    DANGER,
    /** Chunk not loaded. Treated as blocked. */
    UNLOADED
}
