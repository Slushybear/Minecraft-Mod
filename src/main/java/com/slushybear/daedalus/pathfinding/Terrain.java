package com.slushybear.daedalus.pathfinding;

/** What the pathfinder sees in one block cell. No Minecraft types, so the search can be tested on its own. */
public enum Terrain {
    /** Nothing in the way: the body can be here and jump through it. Includes plants, carpets and open gates. */
    AIR,
    /** A full-height block: something to stand on, and a wall to the body. */
    SOLID,
    /** In the way but not something to stand on at block height: fences, walls, bottom slabs, closed gates. */
    BLOCKED,
    /** Still water. Swimmable, and breaks a fall. */
    WATER,
    /** Ladders, vines and similar: the body occupies the cell and can climb it. */
    CLIMBABLE,
    /** Never stand in, on or next to by choice: lava, fire, magma, cactus, cobweb, berry bushes, flowing water. */
    DANGER,
    /** Chunk not loaded. Treated as blocked. */
    UNLOADED
}
