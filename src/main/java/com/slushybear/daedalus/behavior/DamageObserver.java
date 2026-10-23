package com.slushybear.daedalus.behavior;

import com.slushybear.daedalus.DaedalusClient;
import com.slushybear.daedalus.automation.MacroManager;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Watches for health loss and records what it can tell about the cause. It never retaliates.
 * With no combat behaviour yet, the safe response to taking damage while a macro owns the controls is to stop it.
 */
public final class DamageObserver {
    private static final boolean STOP_MACROS_ON_DAMAGE = true;
    private static final double NEARBY_RADIUS = 8.0;
    private static final int HISTORY_SIZE = 20;

    private static final Deque<DamageObservation> HISTORY = new ArrayDeque<>();
    private static float lastHealth = -1;

    private DamageObserver() {}

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            lastHealth = -1;
            return;
        }
        float health = player.getHealth();
        if (lastHealth >= 0 && health < lastHealth) {
            observe(client, player, lastHealth - health, health);
        }
        lastHealth = health;
    }

    /** Most recent observations, oldest first. For a future debug overlay. */
    public static List<DamageObservation> recent() {
        return List.copyOf(HISTORY);
    }

    private static void observe(Minecraft client, LocalPlayer player, float amount, float healthAfter) {
        String category = "unknown";
        String attacker = "";
        String direct = "";
        DamageObservation.Confidence confidence = DamageObservation.Confidence.UNKNOWN;

        DamageSource source = player.getLastDamageSource();
        if (source != null) {
            category = source.getMsgId();
            Entity cause = source.getEntity();
            Entity directCause = source.getDirectEntity();
            if (cause != null) {
                attacker = cause.getName().getString();
                confidence = DamageObservation.Confidence.REPORTED;
            }
            if (directCause != null) {
                direct = directCause.getName().getString();
            }
        }

        List<String> hostiles = client.level == null
                ? List.of()
                : client.level.getEntities(player, player.getBoundingBox().inflate(NEARBY_RADIUS), entity -> entity instanceof Enemy)
                        .stream()
                        .map(entity -> entity.getName().getString())
                        .toList();

        DamageObservation observation = new DamageObservation(
                client.level == null ? 0 : client.level.getGameTime(),
                amount, healthAfter, category, attacker, direct, confidence, hostiles);
        HISTORY.addLast(observation);
        while (HISTORY.size() > HISTORY_SIZE) {
            HISTORY.removeFirst();
        }
        DaedalusClient.LOGGER.info("Damage {} ({}): attacker='{}' direct='{}' confidence={} nearby hostiles={}",
                amount, category, attacker, direct, confidence, hostiles);

        if (STOP_MACROS_ON_DAMAGE) {
            MacroManager.interruptControllers(client, "took damage (" + category + ")");
        }
    }
}
