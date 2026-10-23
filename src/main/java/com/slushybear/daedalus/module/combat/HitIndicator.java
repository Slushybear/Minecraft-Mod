package com.slushybear.daedalus.module.combat;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.ChoiceSetting;
import com.slushybear.daedalus.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/** Flashes a marker around the crosshair each time you hit something, in the theme's accent colour. */
public final class HitIndicator extends Module {
    private static final long SHOW_MS = 300;

    private final BoolSetting sound = addSetting(new BoolSetting("Sound", false));
    private final ChoiceSetting style = addSetting(new ChoiceSetting("Style", "Cross", "Cross", "Dot", "Ring"));
    private long lastHit;

    public HitIndicator() {
        super("Hit indicator", "Flashes the crosshair on hit", Category.COMBAT, false);
    }

    @Override
    public void init() {
        CombatTracker.onAttack(target -> {
            if (!enabled()) {
                return;
            }
            lastHit = System.currentTimeMillis();
            if (sound.get()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT, 1.8f));
            }
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("daedalus", "hit_indicator"), this::draw);
    }

    private void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        long age = System.currentTimeMillis() - lastHit;
        if (!enabled() || age > SHOW_MS) {
            return;
        }
        int color = Theme.withAlpha(Theme.accent(), 1f - age / (float) SHOW_MS);
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        switch (style.get()) {
            case "Dot" -> g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
            case "Ring" -> {
                // A circle of radius 6, one pixel per step around it.
                for (int i = 0; i < 48; i++) {
                    double a = i * Math.PI * 2 / 48;
                    int px = cx + (int) Math.round(Math.cos(a) * 6);
                    int py = cy + (int) Math.round(Math.sin(a) * 6);
                    g.fill(px, py, px + 1, py + 1, color);
                }
            }
            default -> {
                // Four short diagonal strokes, leaving a gap around the crosshair.
                for (int i = 3; i <= 6; i++) {
                    g.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, color);
                    g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
                    g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
                    g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                }
            }
        }
    }
}
