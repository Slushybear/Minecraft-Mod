package com.slushybear.daedalus.module.visuals;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Finds target blocks around the player a little at a time, so scanning never causes a lag spike.
 *
 * A "pass" visits every 16x16x16 chunk section within range, nearest first. Sections that cannot contain a target
 * block are skipped almost for free; only sections that might contain one have their 4096 blocks read, and only a few
 * of those are read per tick. When a pass finishes, its results replace the previous pass's, and a new pass starts
 * from wherever the player is now.
 */
final class BlockScanner {
    /** Sections looked at per tick, most of which are skipped cheaply. */
    private static final int SECTIONS_PER_TICK = 256;
    /** Sections whose blocks are actually read per tick. Each is 4096 block reads. */
    private static final int READS_PER_TICK = 12;
    private static final int MAX_FOUND = 8192;

    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private Long2IntOpenHashMap pending = new Long2IntOpenHashMap();
    private Long2IntOpenHashMap results = new Long2IntOpenHashMap();
    private Map<Block, Integer> targets = Map.of();
    private BlockPos center = BlockPos.ZERO;
    private int range;
    private boolean firstPass = true;

    /** Starts over with new targets. Old results are dropped straight away, since they may be for other blocks. */
    void restart(Map<Block, Integer> newTargets) {
        targets = newTargets;
        queue.clear();
        pending = new Long2IntOpenHashMap();
        results = new Long2IntOpenHashMap();
        firstPass = true;
    }

    void clear() {
        restart(Map.of());
    }

    /** Found blocks, as BlockPos.asLong to colour. */
    Long2IntOpenHashMap results() {
        return results;
    }

    /**
     * Does one tick of scanning. Returns true when the results changed: a pass finished, or during the very first pass
     * so blocks appear while it is still going.
     */
    boolean tick(ClientLevel level, BlockPos player, int range) {
        if (targets.isEmpty()) {
            return false;
        }
        if (queue.isEmpty()) {
            boolean hadPass = !firstPass || !pending.isEmpty();
            if (hadPass) {
                results = pending;
                pending = new Long2IntOpenHashMap();
                firstPass = false;
            }
            startPass(level, player, range);
            if (hadPass) {
                return true;
            }
        }
        int looked = 0;
        int read = 0;
        while (!queue.isEmpty() && looked < SECTIONS_PER_TICK && read < READS_PER_TICK) {
            looked++;
            if (scanSection(level, queue.dequeueLong())) {
                read++;
            }
        }
        if (firstPass) {
            results = pending.clone();
            return true;
        }
        return false;
    }

    private void startPass(ClientLevel level, BlockPos player, int newRange) {
        center = player;
        range = newRange;
        int sx = SectionPos.blockToSectionCoord(player.getX());
        int sy = SectionPos.blockToSectionCoord(player.getY());
        int sz = SectionPos.blockToSectionCoord(player.getZ());
        int r = (newRange >> 4) + 1;
        int minY = level.getMinSectionY();
        int maxY = level.getMaxSectionY();
        List<long[]> sections = new ArrayList<>();
        for (int x = sx - r; x <= sx + r; x++) {
            for (int z = sz - r; z <= sz + r; z++) {
                for (int y = Math.max(minY, sy - r); y <= Math.min(maxY, sy + r); y++) {
                    long distSq = (long) (x - sx) * (x - sx) + (long) (y - sy) * (y - sy) + (long) (z - sz) * (z - sz);
                    sections.add(new long[] {SectionPos.asLong(x, y, z), distSq});
                }
            }
        }
        sections.sort(Comparator.comparingLong(s -> s[1]));
        for (long[] s : sections) {
            queue.enqueue(s[0]);
        }
    }

    /** Scans one section. Returns true if its blocks had to be read (the expensive case). */
    private boolean scanSection(ClientLevel level, long sectionPos) {
        int sx = SectionPos.x(sectionPos);
        int sy = SectionPos.y(sectionPos);
        int sz = SectionPos.z(sectionPos);
        LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
        if (chunk == null) {
            return false;
        }
        int index = sy - level.getMinSectionY();
        LevelChunkSection[] sections = chunk.getSections();
        if (index < 0 || index >= sections.length) {
            return false;
        }
        LevelChunkSection section = sections[index];
        if (section.hasOnlyAir() || !section.maybeHas(state -> targets.containsKey(state.getBlock()))) {
            return false;
        }
        long maxDistSq = (long) range * range;
        int baseX = sx << 4;
        int baseY = sy << 4;
        int baseZ = sz << 4;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    Integer color = targets.get(state.getBlock());
                    if (color == null) {
                        continue;
                    }
                    int bx = baseX + x;
                    int by = baseY + y;
                    int bz = baseZ + z;
                    long dx = bx - center.getX();
                    long dy = by - center.getY();
                    long dz = bz - center.getZ();
                    if (dx * dx + dy * dy + dz * dz <= maxDistSq && pending.size() < MAX_FOUND) {
                        pending.put(BlockPos.asLong(bx, by, bz), (int) color);
                    }
                }
            }
        }
        return true;
    }
}
