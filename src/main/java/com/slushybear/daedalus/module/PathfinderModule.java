package com.slushybear.daedalus.module;

import com.slushybear.daedalus.automation.PathDisplay;
import com.slushybear.daedalus.automation.PathLog;
import com.slushybear.daedalus.automation.WorldView;
import com.slushybear.daedalus.pathfinding.Terrain;
import com.slushybear.daedalus.module.setting.BoolSetting;
import com.slushybear.daedalus.module.setting.SliderSetting;
import com.slushybear.daedalus.pathfinding.Cell;
import com.slushybear.daedalus.pathfinding.MoveType;
import com.slushybear.daedalus.pathfinding.Waypoint;
import com.slushybear.daedalus.ui.Theme;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.RectGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shows where the pathfinder is going, and turns its detailed log on and off.
 *
 * <p>The route ESP highlights, in the menu's gold, the top of each floor block the pathfinder will step on next, in
 * order: the next block brightest, fading along the route, with a thin gold line joining them so the order is easy to
 * follow (lifted into an arc over jumps). Swimming and ladder cells, which have no floor, get a thin outline instead,
 * and the last block of the route gets a stronger frame. Only the part still ahead is drawn, straight from the route the
 * walker is following ({@link PathDisplay}), so it moves on as the player does, changes the moment the route changes,
 * and disappears when no walk is running.
 */
public final class PathfinderModule extends Module implements WorldGizmos.Drawer {
    /** Floor highlights sit this far above the block top, so they never fight with it for the same pixels. */
    private static final double LIFT = 0.012;
    /** Floor highlights are inset this much from the block's edge, so neighbours read as separate steps. */
    private static final double INSET = 0.06;
    /** How high the route line rises over a jump, at its middle. */
    private static final double JUMP_ARC = 0.9;
    private static final double MAX_DISTANCE = 96;

    private final BoolSetting esp = addSetting("Route ESP", "Highlight the blocks the pathfinder is about to walk on", new BoolSetting("Show route", true));
    private final SliderSetting ahead = addSetting("Route ESP", "How many blocks of the route to show", new SliderSetting("Blocks ahead", 40, 5, 120, 5).unit(" blocks"));
    private final BoolSetting throughWalls = addSetting("Route ESP", "Show the route through blocks in the way", new BoolSetting("Through walls", true));
    private final BoolSetting line = addSetting("Route ESP", "A line joining the blocks in order", new BoolSetting("Route line", true));
    private final BoolSetting debugLog = addSetting("Developer", "Write every move, state change and rejected move to logs/daedalus-pathfinder.log", new BoolSetting("Debug log", false));

    public PathfinderModule() {
        super("Pathfinder", "Gold route ESP and the pathfinder's developer log", Category.MISC, true);
    }

    @Override
    public void tick(Minecraft client) {
        PathLog.setDebug(debugLog.get());
    }

    @Override
    protected void onDisable() {
        PathLog.setDebug(false);
    }

    @Override
    public void drawWorld(float partialTick) {
        PathDisplay.Snapshot route = PathDisplay.current();
        if (!esp.get() || route == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        Vec3 eye = client.gameRenderer.mainCamera().position();
        List<Waypoint> waypoints = route.waypoints();
        int first = Math.max(1, route.next());
        int last = Math.min(waypoints.size() - 1, first + ahead.get().intValue() - 1);
        if (first > last) {
            return;
        }
        boolean onTop = throughWalls.get();
        WorldView world = new WorldView(client.level);
        int gold = Theme.accent();
        int bright = Theme.accentLight();

        Vec3 previous = point(world, waypoints.get(first - 1).cell());
        for (int i = first; i <= last; i++) {
            Waypoint waypoint = waypoints.get(i);
            Cell cell = waypoint.cell();
            Vec3 here = point(world, cell);
            if (here.distanceTo(eye) > MAX_DISTANCE) {
                break;
            }
            // Brightest on the next block, fading to a third further along, so the order reads at a glance.
            float t = last == first ? 0 : (float) (i - first) / (last - first);
            float strength = 1 - 0.65f * t;
            boolean next = i == first;
            boolean end = i == waypoints.size() - 1;

            if (hasFloor(world, cell)) {
                Vec3 min = new Vec3(cell.x() + INSET, cell.y() - 1, cell.z() + INSET);
                Vec3 max = new Vec3(cell.x() + 1 - INSET, cell.y() + LIFT, cell.z() + 1 - INSET);
                GizmoStyle style = GizmoStyle.strokeAndFill(Theme.withAlpha(next ? bright : gold, 0.95f * strength),
                        next ? 2.5f : 1.6f, Theme.withAlpha(gold, (next ? 0.42f : 0.26f) * strength));
                show(Gizmos.addGizmo(RectGizmo.fromCuboidFace(min, max, Direction.UP, style)), onTop);
            } else {
                AABB box = new AABB(cell.x() + 0.2, cell.y() + 0.2, cell.z() + 0.2, cell.x() + 0.8, cell.y() + 0.8, cell.z() + 0.8);
                show(Gizmos.cuboid(box, GizmoStyle.stroke(Theme.withAlpha(gold, 0.85f * strength), 1.4f)), onTop);
            }
            if (end) {
                AABB frame = new AABB(cell.x(), cell.y() - 1, cell.z(), cell.x() + 1, cell.y() + LIFT, cell.z() + 1);
                show(Gizmos.cuboid(frame, GizmoStyle.stroke(Theme.withAlpha(bright, 1f), 3f)), onTop);
            }
            if (line.get()) {
                int color = Theme.withAlpha(gold, 0.9f * strength);
                if (isJump(waypoint.type())) {
                    Vec3 top = previous.add(here).scale(0.5).add(0, JUMP_ARC, 0);
                    show(Gizmos.line(previous, top, color, 2f), onTop);
                    show(Gizmos.line(top, here, color, 2f), onTop);
                } else {
                    show(Gizmos.line(previous, here, color, 2f), onTop);
                }
            }
            previous = here;
        }
    }

    /** Where the route line passes through a cell: just above the floor, or the cell's middle in water or on a ladder. */
    private static Vec3 point(WorldView world, Cell cell) {
        double y = hasFloor(world, cell) ? cell.y() + 0.06 : cell.y() + 0.5;
        return new Vec3(cell.x() + 0.5, y, cell.z() + 0.5);
    }

    /**
     * True if the cell is open air standing on a solid block, read from the world rather than guessed from the move: a
     * drop can end in water and a walk can end on a ladder, and neither has a floor to light up.
     */
    private static boolean hasFloor(WorldView world, Cell cell) {
        return world.terrainAt(cell.x(), cell.y(), cell.z()) == Terrain.AIR
                && world.terrainAt(cell.x(), cell.y() - 1, cell.z()) == Terrain.SOLID;
    }

    private static boolean isJump(MoveType type) {
        return type == MoveType.PARKOUR || type == MoveType.RUN_JUMP || type == MoveType.NEO || type == MoveType.SIDE_JUMP;
    }

    private static void show(GizmoProperties gizmo, boolean onTop) {
        if (onTop) {
            gizmo.setAlwaysOnTop();
        }
    }
}
