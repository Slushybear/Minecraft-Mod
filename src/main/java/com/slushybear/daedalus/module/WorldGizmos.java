package com.slushybear.daedalus.module;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;

/**
 * Lets modules draw boxes, lines and floating text in the world.
 *
 * Minecraft 26.2 has a built-in "gizmo" system for this (it is what the debug overlays use). Once per frame, after the
 * game has gathered what to draw, every enabled {@link Drawer} module adds its gizmos, and they are handed to the
 * game's renderer for that frame only.
 */
public final class WorldGizmos {
    /** A module that draws in the world. Called once per frame while the module is enabled. */
    public interface Drawer {
        void drawWorld(float partialTick);
    }

    private WorldGizmos() {}

    /** True while any module is drawing gizmos, so the renderer can keep them clear of the fog. */
    public static boolean active() {
        for (Module module : ModuleRegistry.all()) {
            if (module.enabled() && module instanceof Drawer) {
                return true;
            }
        }
        return false;
    }

    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(WorldGizmos::frame);
    }

    private static void frame(LevelExtractionContext context) {
        float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (Gizmos.TemporaryCollection ignored = Gizmos.withCollector(collector)) {
            for (Module module : ModuleRegistry.all()) {
                if (module.enabled() && module instanceof Drawer drawer) {
                    drawer.drawWorld(partialTick);
                }
            }
        }
        context.levelRenderer().addMainThreadGizmos(collector.drainGizmos());
    }
}
