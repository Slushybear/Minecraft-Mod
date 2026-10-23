package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Third person camera you can swing around with the mouse while your player keeps facing the same way.
 * Bind a key to it in the menu to flick it on and off. The camera is placed by
 * {@link com.slushybear.daedalus.mixin.CameraMixin} and mouse turning is redirected by
 * {@link com.slushybear.daedalus.mixin.EntityMixin}.
 */
public final class Freelook extends Module {
    private final BoolSetting invert = addSetting(new BoolSetting("Invert", false));
    private final SliderSetting sensitivity = addSetting(new SliderSetting("Sensitivity", 100, 25, 200, 5));
    private boolean started;
    private float yaw;
    private float pitch;

    public Freelook() {
        super("Freelook", "Look around without turning", Category.VISUALS, false);
    }

    @Override
    protected void onDisable() {
        started = false;
    }

    public boolean active() {
        var player = Minecraft.getInstance().player;
        if (!enabled() || player == null) {
            started = false;
            return false;
        }
        if (!started) {
            started = true;
            yaw = player.getYRot();
            pitch = player.getXRot();
        }
        return true;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void turn(double dx, double dy) {
        float scale = 0.15f * sensitivity.get().floatValue() / 100f;
        yaw += (float) dx * scale;
        pitch = Mth.clamp(pitch + (float) dy * scale * (invert.get() ? -1 : 1), -90, 90);
    }
}
