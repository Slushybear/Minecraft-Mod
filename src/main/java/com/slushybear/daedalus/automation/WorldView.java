package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.pathfinding.BlockView;
import com.slushybear.daedalus.pathfinding.Terrain;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reads the client world for the pathfinder and sorts every block into a {@link Terrain}. Read only, and only ever used
 * on the client thread: the worker searches a copy (GridSnapshot), never this.
 */
public final class WorldView implements BlockView {
    /** Blocks the walker must never stand in or walk into: they hurt, trap, or teleport. */
    private static final Set<Block> DANGER = Set.of(
            Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.MAGMA_BLOCK, Blocks.CACTUS, Blocks.SWEET_BERRY_BUSH, Blocks.COBWEB,
            Blocks.POWDER_SNOW, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.WITHER_ROSE, Blocks.BUBBLE_COLUMN,
            Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY);

    /** Collision this low (carpet, a thin layer of snow) is walked over as if it were air. */
    private static final double THIN = 0.2;
    /** Collision lower than this (bottom slabs, piled snow) is not something to stand on at full block height. */
    private static final double FULL_ENOUGH = 0.75;

    private final ClientLevel level;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public WorldView(ClientLevel level) {
        this.level = level;
    }

    @Override
    public Terrain terrainAt(int x, int y, int z) {
        pos.set(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return Terrain.UNLOADED;
        }
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (fluid.is(FluidTags.LAVA) || DANGER.contains(state.getBlock())) {
            return Terrain.DANGER;
        }
        // Ladders have a thin collision shape, so they must be recognised before the shape checks below.
        if (state.is(BlockTags.CLIMBABLE)) {
            return Terrain.CLIMBABLE;
        }

        VoxelShape shape = state.getCollisionShape(level, pos);
        if (fluid.is(FluidTags.WATER) && shape.isEmpty()) {
            // Still water is swimmable. Flowing water pushes the body around, so the walker keeps out of it.
            return fluid.isSource() ? Terrain.WATER : Terrain.DANGER;
        }
        if (shape.isEmpty()) {
            return Terrain.AIR;
        }
        double top = shape.max(Direction.Axis.Y);
        if (top <= THIN) {
            return Terrain.AIR;
        }
        if (top > 1.0 || top < FULL_ENOUGH) {
            return Terrain.BLOCKED; // fences and walls are too tall to step onto, slabs too low to stand on at block height
        }
        return Terrain.SOLID;
    }
}
