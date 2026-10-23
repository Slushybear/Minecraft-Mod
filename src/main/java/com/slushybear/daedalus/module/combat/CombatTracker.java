package com.slushybear.daedalus.module.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Remembers your most recent attack: who you hit, when, and whether it looked like a critical hit.
 * The hit indicator, damage numbers and target HUD all read from here.
 */
public final class CombatTracker {
    private static final List<Consumer<Entity>> LISTENERS = new ArrayList<>();
    private static int lastTargetId = -1;
    private static long lastAttackTick = Long.MIN_VALUE;
    private static boolean lastWasCrit;

    private CombatTracker() {}

    public static void init() {
        // This event fires on both the client and, in singleplayer, the built-in server. Only the client one matters.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) {
                onAttack(player, level, entity);
            }
            return InteractionResult.PASS;
        });
    }

    /** Runs whenever you attack something. */
    public static void onAttack(Consumer<Entity> listener) {
        LISTENERS.add(listener);
    }

    private static void onAttack(Player player, Level level, Entity target) {
        lastTargetId = target.getId();
        lastAttackTick = level.getGameTime();
        lastWasCrit = looksLikeCrit(player);
        LISTENERS.forEach(listener -> listener.accept(target));
    }

    /**
     * The vanilla rules for a critical hit: falling, not on the ground, a ladder or in water, not riding, not blind,
     * not sprinting, and the attack cooldown mostly charged. The server decides for real; this is the client's best guess.
     */
    private static boolean looksLikeCrit(Player player) {
        return player.fallDistance > 0
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.isPassenger()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isSprinting()
                && player.getAttackStrengthScale(0.5f) > 0.9f;
    }

    /** The entity you attacked within the last maxTicks, or null. */
    public static Entity recentTarget(Level level, int maxTicks) {
        if (level == null || level.getGameTime() - lastAttackTick > maxTicks) {
            return null;
        }
        return level.getEntity(lastTargetId);
    }

    /** True if you hit this entity within the last maxTicks with what looked like a crit. */
    public static boolean recentCritOn(Entity entity, int maxTicks) {
        return entity.getId() == lastTargetId
                && entity.level().getGameTime() - lastAttackTick <= maxTicks
                && lastWasCrit;
    }
}
