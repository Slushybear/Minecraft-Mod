package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera so you can fly it around while your player stands still. WASD moves where you look,
 * Space goes up, Sneak goes down, Sprint doubles the speed. Your player gets no movement input meanwhile.
 *
 * The camera is placed by {@link com.slushybear.daedalus.mixin.CameraMixin}, mouse turning is redirected by
 * {@link com.slushybear.daedalus.mixin.EntityMixin} and movement keys are blocked by
 * {@link com.slushybear.daedalus.mixin.KeyboardInputMixin}.
 */
public final class Freecam extends Module {
    /** Blocks per tick at speed 1. */
    private static final double BASE_SPEED = 0.5;

    private final SliderSetting speed = addSetting(new SliderSetting("Speed", 1, 0.1, 5, 0.1));
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
        double forward = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double strafe = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        double up = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        if (forward == 0 && strafe == 0 && up == 0) {
            return;
        }
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 left = Vec3.directionFromRotation(0, yaw - 90);
        Vec3 move = look.scale(forward).add(left.scale(strafe)).add(0, up, 0);
        double blocksPerTick = BASE_SPEED * speed.get() * (o.keySprint.isDown() ? 2 : 1);
        pos = pos.add(move.normalize().scale(blocksPerTick));
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
