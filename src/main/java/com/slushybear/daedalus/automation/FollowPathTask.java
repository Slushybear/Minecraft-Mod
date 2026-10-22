package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.pathfinding.Cell;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Walks a path. Each tick it turns toward the next cell, holds forward, and jumps when the next cell is higher.
 * It fails if it stops getting closer for too long, or if the path takes too long.
 */
public final class FollowPathTask implements Task {
    /** Counts as reached when within about 0.3 blocks horizontally. */
    private static final double ARRIVE_DISTANCE_SQ = 0.09;
    private static final int STUCK_LIMIT_TICKS = 40;

    private final List<Cell> path;
    /** path[0] is where the walk started, so begin at the next cell. */
    private int index = 1;
    private int elapsed;
    private int stuckTicks;
    private double bestDistanceSq = Double.MAX_VALUE;
    private String failure;

    public FollowPathTask(List<Cell> path) {
        this.path = path;
    }

    @Override
    public boolean tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return fail(client, "lost the player");
        }
        if (++elapsed > Limits.GOTO_TIMEOUT_TICKS) {
            return fail(client, "timed out");
        }
        if (index >= path.size()) {
            release(client);
            return true;
        }

        Cell next = path.get(index);
        double dx = next.x() + 0.5 - player.getX();
        double dz = next.z() + 0.5 - player.getZ();
        double dy = next.y() - player.getY();
        double distanceSq = dx * dx + dz * dz;

        if (distanceSq < ARRIVE_DISTANCE_SQ && Math.abs(dy) < 1.0) {
            index++;
            stuckTicks = 0;
            bestDistanceSq = Double.MAX_VALUE;
            return false;
        }

        if (distanceSq < bestDistanceSq - 0.001) {
            bestDistanceSq = distanceSq;
            stuckTicks = 0;
        } else if (++stuckTicks > STUCK_LIMIT_TICKS) {
            return fail(client, "got stuck near " + next.x() + " " + next.y() + " " + next.z());
        }

        // Minecraft yaw: forward is (-sin yaw, cos yaw), so yaw = atan2(-dx, dz).
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot(0);
        client.options.keyUp.setDown(true);
        client.options.keyJump.setDown(dy > 0.5 && player.onGround());
        return false;
    }

    @Override
    public void cancel(Minecraft client) {
        release(client);
    }

    @Override
    public boolean failed() {
        return failure != null;
    }

    @Override
    public String failure() {
        return failure == null ? "" : failure;
    }

    private void release(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyJump.setDown(false);
    }

    private boolean fail(Minecraft client, String reason) {
        failure = reason;
        release(client);
        return true;
    }
}
