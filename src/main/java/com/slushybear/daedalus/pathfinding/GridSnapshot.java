package com.slushybear.daedalus.pathfinding;

/**
 * An immutable copy of the terrain in a box. Capture it on the thread that owns the world, then search it
 * from any thread. Cells outside the box read as UNLOADED, so a search never wanders beyond what was copied.
 */
public final class GridSnapshot implements BlockView {
    /** Keeps one snapshot to a few megabytes. */
    private static final int MAX_VOLUME = 4_000_000;
    private static final Terrain[] TERRAIN = Terrain.values();

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final byte[] cells;

    private GridSnapshot(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, byte[] cells) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = cells;
    }

    /** Copies every cell from min to max inclusive. Throws IllegalArgumentException if the box is empty or too large. */
    public static GridSnapshot capture(BlockView source, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long sizeX = (long) maxX - minX + 1;
        long sizeY = (long) maxY - minY + 1;
        long sizeZ = (long) maxZ - minZ + 1;
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeX * sizeY * sizeZ > MAX_VOLUME) {
            throw new IllegalArgumentException("Snapshot box is empty or larger than " + MAX_VOLUME + " cells");
        }

        byte[] cells = new byte[(int) (sizeX * sizeY * sizeZ)];
        int i = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cells[i++] = (byte) source.terrainAt(x, y, z).ordinal();
                }
            }
        }
        return new GridSnapshot(minX, minY, minZ, (int) sizeX, (int) sizeY, (int) sizeZ, cells);
    }

    @Override
    public Terrain terrainAt(int x, int y, int z) {
        int dx = x - minX;
        int dy = y - minY;
        int dz = z - minZ;
        if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ) {
            return Terrain.UNLOADED;
        }
        return TERRAIN[cells[(dx * sizeY + dy) * sizeZ + dz]];
    }
}
