package com.slushybear.daedalus.module.combat;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.WorldGizmos;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.SliderSetting;
import com.slushybear.daedalus.ui.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Shows how much damage mobs and players take, as numbers that float up from them and fade out.
 * The server sends every nearby mob's health to the client, so a drop in health between two ticks is the damage.
 */
public final class DamageNumbers extends Module implements WorldGizmos.Drawer {
    private static final double RANGE = 32;
    private static final long LIFE_MS = 1000;
    private static final int MAX_NUMBERS = 64;
    private static final int CRIT_WINDOW_TICKS = 10;
    private static final int CRIT_COLOR = 0xFFFFC94A;

    private final SliderSetting scale = addSetting("Display", "Size of the numbers", new SliderSetting("Scale", 1, 0.5, 2, 0.1).unit("x"));
    private final BoolSetting critsOnly = addSetting("Display", "Only show critical hits", new BoolSetting("Crits only", false));

    private Map<Integer, Float> lastHealth = new HashMap<>();
    private final List<Popup> popups = new ArrayList<>();

    private record Popup(Vec3 pos, String text, boolean crit, long born) {}

    public DamageNumbers() {
        super("Damage numbers", "Shows damage dealt above mobs", Category.COMBAT, false);
    }

    @Override
    protected void onDisable() {
        lastHealth.clear();
        popups.clear();
    }

    @Override
    public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            lastHealth.clear();
            return;
        }
        Map<Integer, Float> now = new HashMap<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == client.player
                    || entity.distanceToSqr(client.player) > RANGE * RANGE) {
                continue;
            }
            float health = living.getHealth();
            Float before = lastHealth.get(entity.getId());
            if (before != null && health < before - 0.01f) {
                boolean crit = CombatTracker.recentCritOn(entity, CRIT_WINDOW_TICKS);
                if (!critsOnly.get() || crit) {
                    spawn(living, before - health, crit);
                }
            }
            now.put(entity.getId(), health);
        }
        lastHealth = now;
    }

    private void spawn(LivingEntity entity, float amount, boolean crit) {
        // A little random sideways offset so several hits in a row do not stack on top of each other.
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3 pos = entity.position().add(random.nextDouble(-0.4, 0.4), entity.getBbHeight() + 0.25, random.nextDouble(-0.4, 0.4));
        String text = amount == Math.floor(amount) ? String.valueOf((int) amount) : String.format("%.1f", amount);
        popups.add(new Popup(pos, crit ? text + "!" : text, crit, System.currentTimeMillis()));
        while (popups.size() > MAX_NUMBERS) {
            popups.removeFirst();
        }
    }

    @Override
    public void drawWorld(float partialTick) {
        long now = System.currentTimeMillis();
        Iterator<Popup> it = popups.iterator();
        while (it.hasNext()) {
            Popup popup = it.next();
            float age = (now - popup.born()) / (float) LIFE_MS;
            if (age >= 1) {
                it.remove();
                continue;
            }
            // Rise about two thirds of a block and fade out faster towards the end.
            Vec3 pos = popup.pos().add(0, age * 0.7, 0);
            int base = popup.crit() ? CRIT_COLOR : 0xFFFFFFFF;
            int color = Theme.withAlpha(base, 1 - age * age);
            float size = TextGizmo.Style.DEFAULT_SCALE * scale.get().floatValue() * (popup.crit() ? 1.25f : 1f);
            Gizmos.billboardText(popup.text(), pos, TextGizmo.Style.forColorAndCentered(color).withScale(size));
        }
    }
}
