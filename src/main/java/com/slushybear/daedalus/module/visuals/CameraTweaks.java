package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.SliderSetting;

/**
 * Small camera changes, read by {@link com.slushybear.daedalus.mixin.CameraMixin} and
 * {@link com.slushybear.daedalus.mixin.GameRendererMixin}.
 */
public final class CameraTweaks extends Module {
    private static final float VANILLA_DISTANCE = 4;

    private final BoolSetting noHurtShake = addSetting("Camera", "Stop the screen tilting when you take damage", new BoolSetting("No hurt shake", true));
    private final BoolSetting clip = addSetting("Third person", "Let the camera pass through blocks", new BoolSetting("Clip through walls", true));
    private final SliderSetting distance = addSetting("Third person", "How far back the camera sits", new SliderSetting("Distance", 4, 1, 16, 0.5).unit(" blocks"));

    public CameraTweaks() {
        super("Camera", "No hurt shake, wall clipping, zoom", Category.VISUALS, false);
    }

    public boolean noHurtShake() {
        return enabled() && noHurtShake.get();
    }

    /** Third person camera goes through blocks instead of being pushed in front of them. */
    public boolean clipThroughWalls() {
        return enabled() && clip.get();
    }

    /** How far behind the player the third person camera sits. Vanilla is 4 blocks. */
    public float distance() {
        return enabled() ? distance.get().floatValue() : VANILLA_DISTANCE;
    }

    /** Multiplier for vanilla's own camera distance (which already accounts for player size). */
    public float distanceScale() {
        return distance() / VANILLA_DISTANCE;
    }
}
