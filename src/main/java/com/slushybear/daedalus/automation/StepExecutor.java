package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.pathfinding.Cell;
import com.slushybear.daedalus.pathfinding.GridSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;

/** Turns one step into a Task. Instant steps finish on their first tick. */
public final class StepExecutor {
    /** Blocks of extra terrain copied around the start and goal for a path search. */
    private static final int SNAPSHOT_MARGIN = 12;

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

    /** Plans a path to the target now, then returns a task that walks it. A failed plan fails the step. */
    private static Task goTo(Minecraft client, Step step) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return Task.failWith("not in a world");
        }

        String[] parts = step.target().split(" ");
        Cell goal = new Cell(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        BlockPos here = player.blockPosition();
        Cell start = new Cell(here.getX(), here.getY(), here.getZ());

        if (Math.max(Math.abs(goal.x() - start.x()), Math.abs(goal.z() - start.z())) > Limits.MAX_PATH_DISTANCE) {
            return Task.failWith("target is more than " + Limits.MAX_PATH_DISTANCE + " blocks away");
        }
        if (Math.abs(goal.y() - start.y()) > Limits.MAX_PATH_HEIGHT) {
            return Task.failWith("target is more than " + Limits.MAX_PATH_HEIGHT + " blocks above or below you");
        }

        // Copy the terrain here on the client thread, so the worker thread never touches the live world.
        // The box covers both ends plus room to go around obstacles and to drop below the lower end.
        GridSnapshot snapshot = GridSnapshot.capture(
                new WorldView(client.level),
                Math.min(start.x(), goal.x()) - SNAPSHOT_MARGIN,
                Math.min(start.y(), goal.y()) - SNAPSHOT_MARGIN,
                Math.min(start.z(), goal.z()) - SNAPSHOT_MARGIN,
                Math.max(start.x(), goal.x()) + SNAPSHOT_MARGIN,
                Math.max(start.y(), goal.y()) + SNAPSHOT_MARGIN,
                Math.max(start.z(), goal.z()) + SNAPSHOT_MARGIN);
        return new PathPlanTask(snapshot, start, goal, step.target());
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
