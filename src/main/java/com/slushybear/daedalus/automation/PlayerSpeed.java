package com.slushybear.daedalus.automation;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.client.player.LocalPlayer;

/**
 * How fast the player moves compared with an unmodified one. Servers with a speed stat (and potions) change the movement
 * speed attribute, which is 0.1 for vanilla walking. Sprinting adds 30% to the attribute while it is on, so that is taken
 * back out: the pathfinder wants the speed without it, and picks sprinting itself.
 */
public final class PlayerSpeed {
    /** The attribute's value for an unmodified walking player. */
    private static final double VANILLA = 0.1;
    private static final double SPRINT_BONUS = 1.3;
    private static final double LOWEST = 0.2;
    private static final double HIGHEST = 20;

    private PlayerSpeed() {}

    public static double factor(LocalPlayer player) {
        double value = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (player.isSprinting()) {
            value /= SPRINT_BONUS;
        }
        double factor = value / VANILLA;
        if (Double.isNaN(factor) || factor <= 0) {
            return 1;
        }
        return Math.max(LOWEST, Math.min(HIGHEST, factor));
    }
}
