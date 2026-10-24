package com.slushybear.daedalus.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PathLogTest {
    @Test
    void linesHaveATimestampAndALevel() {
        String line = PathLog.line(LocalDateTime.of(2026, 10, 23, 22, 41, 7, 123_000_000), PathLog.Level.WARN, "route blocked");

        assertEquals("2026-10-23 22:41:07.123 [WARN] route blocked", line);
    }

    @Test
    void placeholdersAreFilledInOrder() {
        assertEquals("move 3/10 to 1 2 3", PathLog.format("move {}/{} to {}", 3, 10, "1 2 3"));
        assertEquals("no placeholders {}", PathLog.format("no placeholders {}"));
    }

    @Test
    void debugIsOffUntilTurnedOn() {
        assertFalse(PathLog.debugEnabled());
    }
}
