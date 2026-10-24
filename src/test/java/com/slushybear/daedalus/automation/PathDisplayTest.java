package com.slushybear.daedalus.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.slushybear.daedalus.pathfinding.Cell;
import com.slushybear.daedalus.pathfinding.MoveType;
import com.slushybear.daedalus.pathfinding.Path;
import com.slushybear.daedalus.pathfinding.Waypoint;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PathDisplayTest {
    private static Path line(int length) {
        List<Waypoint> waypoints = new java.util.ArrayList<>();
        for (int x = 0; x <= length; x++) {
            waypoints.add(new Waypoint(new Cell(x, 1, 0), MoveType.WALK, x == 0 ? 0 : 4));
        }
        return new Path(waypoints);
    }

    @AfterEach
    void clear() {
        PathDisplay.clear();
    }

    @Test
    void showsOnlyWhatIsStillAhead() {
        Path path = line(5);

        PathDisplay.show(path, 2);

        assertEquals(3, PathDisplay.current().next(), "moves 0 and 1 are behind; the walker is heading for waypoint 3");
        assertSame(path.waypoints(), PathDisplay.current().waypoints());
    }

    @Test
    void movesOnAsThePlayerProgresses() {
        Path path = line(5);
        PathDisplay.show(path, 0);
        PathDisplay.Snapshot before = PathDisplay.current();

        PathDisplay.show(path, 1);

        assertNotSame(before, PathDisplay.current());
        assertEquals(2, PathDisplay.current().next());
    }

    @Test
    void doesNotRebuildWhenNothingChanged() {
        Path path = line(5);
        PathDisplay.show(path, 1);
        PathDisplay.Snapshot first = PathDisplay.current();

        PathDisplay.show(path, 1);

        assertSame(first, PathDisplay.current());
    }

    @Test
    void aNewRouteReplacesTheOldOneAtOnce() {
        PathDisplay.show(line(5), 3);
        Path replacement = line(8);

        PathDisplay.show(replacement, 0);

        assertSame(replacement.waypoints(), PathDisplay.current().waypoints());
        assertEquals(1, PathDisplay.current().next());
    }

    @Test
    void clearingLeavesNothingToDraw() {
        PathDisplay.show(line(5), 0);

        PathDisplay.clear();

        assertNull(PathDisplay.current());
    }
}
