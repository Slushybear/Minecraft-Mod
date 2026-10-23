package com.slushybear.daedalus.module.visuals;

import com.slushybear.daedalus.module.Category;
import com.slushybear.daedalus.module.Module;
import com.slushybear.daedalus.module.WorldGizmos;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.ChoiceSetting;
import com.slushybear.daedalus.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Highlights players and mobs through walls. Outline and Box draw a box around each one; Glow uses the game's own
 * glowing effect (the one from spectral arrows), coloured by type. Players use the accent colour, hostile mobs red
 * and other mobs green. Glow is handled by {@link com.slushybear.daedalus.mixin.MinecraftMixin} and
 * {@link com.slushybear.daedalus.mixin.EntityMixin}.
 */
public final class MobEsp extends Module implements WorldGizmos.Drawer {
    private static final double RANGE = 96;
    private static final int HOSTILE = 0xFFef4444;
    private static final int PASSIVE = 0xFF34d399;

    private final ChoiceSetting mode = addSetting(new ChoiceSetting("Mode", "Outline", "Outline", "Box", "Glow"));
    private final BoolSetting players = addSetting(new BoolSetting("Players", true));
    private final BoolSetting mobs = addSetting(new BoolSetting("Mobs", true));

    public MobEsp() {
        super("Mob ESP", "Highlights mobs and players", Category.VISUALS, false);
    }

    private boolean matches(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || entity == client.player || !(entity instanceof LivingEntity) || !entity.isAlive()
                || entity.distanceToSqr(client.player) > RANGE * RANGE) {
            return false;
        }
        return (players.get() && entity instanceof Player) || (mobs.get() && entity instanceof Mob);
    }

    public int colorFor(Entity entity) {
        if (entity instanceof Player) {
            return Theme.accent();
        }
        return entity instanceof Enemy ? HOSTILE : PASSIVE;
    }

    /** True if this entity should get the glowing outline. */
    public boolean glows(Entity entity) {
        return enabled() && mode.get().equals("Glow") && matches(entity);
    }

    @Override
    public void drawWorld(float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || mode.get().equals("Glow")) {
            return;
        }
        boolean filled = mode.get().equals("Box");
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!matches(entity)) {
                continue;
            }
            // Move the box to where the entity is drawn this frame, between its last two tick positions.
            Vec3 shift = entity.getPosition(partialTick).subtract(entity.position());
            AABB box = entity.getBoundingBox().move(shift).inflate(0.05);
            int color = colorFor(entity);
            GizmoStyle style = filled
                    ? GizmoStyle.strokeAndFill(color, 2f, Theme.withAlpha(color, 0.22f))
                    : GizmoStyle.stroke(color, 2f);
            Gizmos.cuboid(box, style).setAlwaysOnTop();
        }
    }
}
