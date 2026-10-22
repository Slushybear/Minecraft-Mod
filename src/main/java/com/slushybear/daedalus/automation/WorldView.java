package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.pathfinding.BlockView;
import com.slushybear.daedalus.pathfinding.Terrain;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Reads the loaded client world for the pathfinder. Read only. */
public final class WorldView implements BlockView {
    private final ClientLevel level;

    public WorldView(ClientLevel level) {
        this.level = level;
    }

    @Override
    public Terrain terrainAt(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return Terrain.UNLOADED;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)) {
            return Terrain.DANGER;
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            return Terrain.WATER;
        }
        return state.getCollisionShape(level, pos).isEmpty() ? Terrain.AIR : Terrain.SOLID;
    }
}
