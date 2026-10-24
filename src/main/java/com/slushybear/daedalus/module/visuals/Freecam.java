package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.KeyNames;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.setting.SliderSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera so you can fly it around while your player stands still. Moves like creative flying:
 * WASD moves level, Jump goes up, Sneak goes down, Sprint doubles the speed. Your player gets no movement input meanwhile.
 *
 * The camera is placed by {@link com.slushybear.daedalus.mixin.CameraMixin}, mouse turning is redirected by
 * {@link com.slushybear.daedalus.mixin.EntityMixin} and movement keys are blocked by
 * {@link com.slushybear.daedalus.mixin.KeyboardInputMixin}.
 */
public final class Freecam extends Module {
    /** Blocks per tick at speed 1. */
    private static final double BASE_SPEED = 0.5;

    private final SliderSetting speed = addSetting("Movement", "Flying speed. Sprint doubles it", new SliderSetting("Speed", 1, 0.1, 5, 0.1).unit("x"));
    private Vec3 pos;
    private Vec3 prevPos;
    private float yaw;
    private float pitch;

    public Freecam() {
        super("Freecam", "Detach the camera and fly it around", Category.VISUALS, false);
    }

    @Override
    protected void onDisable() {
        pos = null;
    }

    /** True while the camera is actually detached (the module is on and you are in a world). */
    public boolean active() {
        return enabled() && pos != null && Minecraft.getInstance().player != null;
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null) {
            pos = null;
            return;
        }
        if (pos == null) {
            // Start at the player's eyes, looking the same way.
            pos = client.player.getEyePosition();
            prevPos = pos;
            yaw = client.player.getYRot();
            pitch = client.player.getXRot();
            return;
        }
        prevPos = pos;
        if (client.gui.screen() != null) {
            return;
        }
        Options o = client.options;
        double forward = held(client, o.keyUp) - held(client, o.keyDown);
        double strafe = held(client, o.keyLeft) - held(client, o.keyRight);
        double up = held(client, o.keyJump) - held(client, o.keyShift);
        if (forward == 0 && strafe == 0 && up == 0) {
            return;
        }
        // Moves like creative flying: WASD stays level whatever the pitch, Jump goes up and Sneak goes down.
        Vec3 ahead = Vec3.directionFromRotation(0, yaw);
        Vec3 left = Vec3.directionFromRotation(0, yaw - 90);
        Vec3 move = ahead.scale(forward).add(left.scale(strafe)).add(0, up, 0);
        double blocksPerTick = BASE_SPEED * speed.get() * (held(client, o.keySprint) > 0 ? 2 : 1);
        pos = pos.add(move.normalize().scale(blocksPerTick));
    }

    /**
     * 1 while the key bound to this control is physically held, otherwise 0. Reading the key itself (instead of
     * KeyMapping.isDown) means the toggle sneak and toggle sprint options do not get in the way.
     */
    private static int held(Minecraft client, KeyMapping mapping) {
        return KeyNames.isDown(client, mapping.saveString()) ? 1 : 0;
    }

    /** Where to draw the camera this frame, between the last two tick positions. */
    public Vec3 cameraPos(float partialTick) {
        return prevPos.lerp(pos, partialTick);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** Mouse movement, in the same units vanilla passes to Entity.turn. */
    public void turn(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90, 90);
    }
}
