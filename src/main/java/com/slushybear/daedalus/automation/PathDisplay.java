package com.slushybear.daedalus.automation;

import com.slushybear.daedalus.pathfinding.Path;
import com.slushybear.daedalus.pathfinding.Waypoint;
import java.util.List;

/**
 * The route the pathfinder is walking right now, for drawing. The navigation task publishes the route and how far along
 * it the player is; the Pathfinder module reads it when it draws. Only the remaining part is shown, and nothing at all
 * between routes (planning, or no walk running), so a stale route is never left on screen.
 *
 * <p>Both sides run on the client thread. The snapshot is replaced, never changed, so a frame always sees one whole
 * route.
 */
public final class PathDisplay {
    /** A route, and the index of the first waypoint still ahead of the player. */
    public record Snapshot(List<Waypoint> waypoints, int next) {}

    private static volatile Snapshot current;

    private PathDisplay() {}

    /** Shows `path` from the move the walker is on. Cheap to call every tick: nothing changes unless progress does. */
    public static void show(Path path, int moveIndex) {
        Snapshot shown = current;
        int next = moveIndex + 1;
        if (shown != null && shown.waypoints() == path.waypoints() && shown.next() == next) {
            return;
        }
        current = new Snapshot(path.waypoints(), next);
    }

    public static void clear() {
        current = null;
    }

    /** The route to draw, or null when there is none. */
    public static Snapshot current() {
        return current;
    }
}
