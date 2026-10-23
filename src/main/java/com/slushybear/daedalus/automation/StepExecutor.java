package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.pathfinding.Cell;
import com.slushybear.daedalus.pathfinding.GoalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;

/** Turns one step into a Task. Instant steps finish on their first tick. */
public final class StepExecutor {
    private StepExecutor() {}

    public static Task start(Minecraft client, Step step) {
        Allowlist allowlist = MacroManager.allowlist();
        if (!allowlist.actions().contains(step.type())) {
            return Task.done();
        }
        return switch (step.type()) {
            case WAIT -> new TimedTask(null, step.amount());
            case HOLD -> HeldKey.byId(step.target())
                    .filter(allowlist.holdKeys()::contains)
                    .<Task>map(key -> new TimedTask(key.mapping(client.options), step.amount()))
                    .orElseGet(Task::done);
            case SWAP_SLOT -> Task.instant(c -> {
                if (c.player != null) {
                    selectSlot(c.player, step.amount() - 1);
                }
            });
            case CHAT -> Task.instant(c -> {
                if (c.player != null && allowlist.chatEnabled()) {
                    c.player.connection.sendChat(step.target());
                }
            });
            case HARVEST_CROPS, REPLANT_CROPS -> {
                boolean replant = step.type() == ActionType.REPLANT_CROPS;
                yield Task.instant(c -> CropType.byId(step.target())
                        .filter(allowlist.crops()::contains)
                        .ifPresent(crop -> FarmRunner.run(c, crop, step.amount(), replant)));
            }
            case GOTO -> goTo(client, step);
            case MINE -> mine(step, allowlist);
        };
    }

    /** Starts planning toward the target now, and returns a task that walks there one segment at a time. */
    private static Task goTo(Minecraft client, Step step) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return Task.failWith("not in a world");
        }

        String[] parts = step.target().split(" ");
        Cell goal = new Cell(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        BlockPos here = player.blockPosition();
        if (Math.max(Math.abs(goal.x() - here.getX()), Math.abs(goal.z() - here.getZ())) > Limits.MAX_PATH_DISTANCE) {
            return Task.failWith("target is more than " + Limits.MAX_PATH_DISTANCE + " blocks away");
        }

        return new NavigationTask(client, new GoalBlock(goal, 1), step.target());
    }

    private static Task mine(Step step, Allowlist allowlist) {
        return MineTarget.byId(step.target())
                .filter(allowlist.mineTargets()::contains)
                .<Task>map(target -> new MineTask(target.blocks(), step.amount()))
                .orElseGet(() -> Task.failWith("unknown or disallowed block '" + step.target() + "'"));
    }

    private static void selectSlot(LocalPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        inventory.setSelectedSlot(slot);
        // Vanilla does not tell the server about a slot change on its own, so send it.
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }
}
