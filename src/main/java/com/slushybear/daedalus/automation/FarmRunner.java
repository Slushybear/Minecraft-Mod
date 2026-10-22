package com.slushybear.daedalus.automation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Harvests or replants crops in a circle around the player. Called once per step, never every tick. */
public final class FarmRunner {
    private FarmRunner() {}

    public static void run(Minecraft client, CropType crop, int radius, boolean replant) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        MultiPlayerGameMode gameMode = client.gameMode;
        if (player == null || level == null || gameMode == null) {
            return;
        }

        BlockPos center = player.blockPosition();
        double radiusSq = (double) radius * radius;
        int actions = 0;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
            if (actions >= Limits.MAX_BLOCK_ACTIONS_PER_STEP) {
                break;
            }
            if (pos.distSqr(center) > radiusSq || !level.hasChunkAt(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);

            if (!replant) {
                if (crop.isMature(state)) {
                    interact(gameMode, player, pos, Direction.UP);
                    actions++;
                }
                continue;
            }

            if (state.isAir()
                    && level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                    && selectSeed(player, crop.seed())) {
                // Right-click the farmland below, which places the seed on the air block above it.
                interact(gameMode, player, pos.below(), Direction.UP);
                actions++;
            }
        }
    }

    private static void interact(MultiPlayerGameMode gameMode, LocalPlayer player, BlockPos pos, Direction face) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** Moves a seed stack into the selected hotbar slot. Returns false if the player has none. */
    private static boolean selectSeed(LocalPlayer player, Item seed) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (inventory.getItem(slot).is(seed)) {
                if (inventory.getSelectedSlot() != slot) {
                    inventory.setSelectedSlot(slot);
                    player.connection.send(new ServerboundSetCarriedItemPacket(slot));
                }
                return true;
            }
        }
        return false;
    }
}
