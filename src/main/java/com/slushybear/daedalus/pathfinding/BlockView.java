package com.slushybear.daedalus.pathfinding;

/** Read-only view of the terrain. The Minecraft side implements it, and tests can use a fake. */
public interface BlockView {
    Terrain terrainAt(int x, int y, int z);
}
