package com.slushybear.daedalus.module.combat;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.setting.ChoiceSetting;
import com.slushybear.daedalus.ui.Draw;
import com.slushybear.daedalus.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A small card showing your current target: whatever you hit in the last 5 seconds, otherwise whatever is under
 * the crosshair. Shows its name, a health bar, its health and how far away it is.
 */
public final class TargetHud extends Module {
    private static final int RECENT_TICKS = 100;
    private static final double MAX_DISTANCE = 32;
    private static final int W = 128;
    private static final int H = 36;

    private final ChoiceSetting position = addSetting("Layout", "Where the panel sits on screen", new ChoiceSetting("Position", "Center", "Top", "Center", "Bottom"));
    /** Smoothed health bar fill, so the bar slides down instead of jumping. */
    private float shownFraction = -1;
    private int shownId = -1;

    public TargetHud() {
        super("Target HUD", "Displays your current target", Category.COMBAT, false);
    }

    @Override
    public void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("daedalus", "target_hud"), this::draw);
    }

    private LivingEntity findTarget(Minecraft client) {
        Entity recent = CombatTracker.recentTarget(client.level, RECENT_TICKS);
        if (recent instanceof LivingEntity living && living.isAlive() && living.distanceTo(client.player) <= MAX_DISTANCE) {
            return living;
        }
        if (client.crosshairPickEntity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    private void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled() || client.player == null || client.level == null) {
            return;
        }
        LivingEntity target = findTarget(client);
        if (target == null) {
            shownId = -1;
            return;
        }
        int x = (g.guiWidth() - W) / 2;
        int y = switch (position.get()) {
            case "Top" -> 6;
            case "Bottom" -> g.guiHeight() - 72 - H;
            default -> g.guiHeight() / 2 + 18;
        };
        Font font = client.font;

        Draw.box(g, x, y, W, H, Theme.withAlpha(Theme.panel(), 0.92f), Theme.line());
        String name = font.plainSubstrByWidth(target.getName().getString(), W - 50);
        g.text(font, name, x + 7, y + 6, Theme.text(), false);
        String distance = String.format("%.1fm", target.distanceTo(client.player));
        g.text(font, distance, x + W - 7 - font.width(distance), y + 6, Theme.muted(), false);

        float max = Math.max(1, target.getMaxHealth());
        float fraction = Math.max(0, Math.min(1, target.getHealth() / max));
        if (shownId != target.getId() || shownFraction < 0) {
            shownId = target.getId();
            shownFraction = fraction;
        }
        shownFraction += (fraction - shownFraction) * 0.2f;

        int barX = x + 7;
        int barY = y + 19;
        int barW = W - 14;
        Draw.round(g, barX, barY, barW, 5, 2, Theme.toggleOff());
        Draw.round(g, barX, barY, Math.max(2, Math.round(barW * shownFraction)), 5, 2, Theme.accent());
        String health = String.format("%.1f / %.0f", target.getHealth(), max);
        g.text(font, health, x + 7, y + 26, Theme.muted(), false);
    }
}
