package com.slushybear.daedalus.automation;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Breaks the nearest matching block in reach and in line of sight, one after another, up to Limits.MAX_BLOCKS_PER_MINE.
 * Finishes when nothing visible matches, when the block cap is hit, or on timeout.
 */
public final class MineTask implements Task {
    /** Vanilla reach is about 4.5 blocks. */
    private static final double REACH_SQ = 4.5 * 4.5;

    private final Set<Block> blocks;
    private final int radius;
    private int mined;
    private int elapsed;
    private BlockPos current;
    private boolean breaking;

    public MineTask(Set<Block> blocks, int radius) {
        this.blocks = blocks;
        this.radius = radius;
    }

    @Override
    public boolean tick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        MultiPlayerGameMode gameMode = client.gameMode;
        if (player == null || level == null || gameMode == null) {
            return finish(client);
        }
        if (++elapsed > Limits.MINE_TIMEOUT_TICKS || mined >= Limits.MAX_BLOCKS_PER_MINE) {
            return finish(client);
        }

        if (current != null && !blocks.contains(level.getBlockState(current).getBlock())) {
            if (level.getBlockState(current).isAir()) {
                mined++;
            }
            current = null;
            breaking = false;
        }
        if (current == null) {
            current = nearestVisibleTarget(level, player);
            if (current == null) {
                return finish(client);
            }
        }

        // The player may have moved or turned, so drop a target that is no longer visible instead of mining through a wall.
        if (!isVisible(level, player, current)) {
            current = null;
            breaking = false;
            gameMode.stopDestroyBlock();
            return false;
        }

        aim(player, current);
        if (!breaking) {
            gameMode.startDestroyBlock(current, Direction.UP);
            breaking = true;
        }
        gameMode.continueDestroyBlock(current, Direction.UP);
        player.swing(InteractionHand.MAIN_HAND);
        return false;
    }

    @Override
    public void cancel(Minecraft client) {
        if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
    }

    private boolean finish(Minecraft client) {
        cancel(client);
        return true;
    }

    private BlockPos nearestVisibleTarget(ClientLevel level, LocalPlayer player) {
        BlockPos origin = player.blockPosition();
        Vec3 eye = player.getEyePosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (!blocks.contains(level.getBlockState(pos).getBlock())) {
                continue;
            }
            double distance = eye.distanceToSqr(Vec3.atCenterOf(pos));
            // The ray is the costly part, so only cast it for candidates that would beat the current best.
            if (distance > REACH_SQ || distance >= bestDistance || !isVisible(level, player, pos)) {
                continue;
            }
            best = pos.immutable();
            bestDistance = distance;
        }
        return best;
    }

    /** True if a ray from the eye to the block's centre hits that block first, with nothing solid in between. */
    private static boolean isVisible(ClientLevel level, LocalPlayer player, BlockPos target) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                Vec3.atCenterOf(target),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        return hit.getBlockPos().equals(target);
    }

    /** Turns the player to face the block, using the same yaw and pitch convention as FollowPathTask. */
    private static void aim(LocalPlayer player, BlockPos target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.getX() + 0.5 - eye.x;
        double dy = target.getY() + 0.5 - eye.y;
        double dz = target.getZ() + 0.5 - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
    }
}
