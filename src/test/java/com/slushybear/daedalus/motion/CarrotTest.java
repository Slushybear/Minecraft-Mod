package com.slushybear.daedalus.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CarrotTest {
    private static final boolean[] NO_STOPS = {false, false, false, false};

    @Test
    void sitsTheGivenDistanceAlongTheLine() {
        double[] xs = {0, 2, 2, 2};
        double[] zs = {0, 0, 2, 4};
        double[] ys = {1, 1, 1, 1};

        Carrot.Point p = Carrot.along(xs, zs, ys, NO_STOPS, 3);

        assertEquals(2, p.x(), 1e-9);
        assertEquals(1, p.z(), 1e-9);
    }

    @Test
    void slidesContinuouslyAsThePlayerMovesAndTheRouteAdvances() {
        // The same route, before and after the walker moves on from cell centre (2,0) to (2,2). The player is on the line
        // in both cases, so the carrot must not jump.
        double[] ys = {1, 1, 1, 1};
        Carrot.Point before = Carrot.along(new double[]{1.9, 2, 2, 2}, new double[]{0, 0, 2, 4}, ys, NO_STOPS, 2.5);
        Carrot.Point after = Carrot.along(new double[]{2, 2, 2, 2}, new double[]{0.1, 2, 2, 4}, ys, NO_STOPS, 2.5);

        assertEquals(before.x(), after.x(), 0.2);
        assertEquals(before.z(), after.z(), 0.2);
    }

    @Test
    void movesSmoothlyWhenThePlayerWalksAlongTheLine() {
        double[] zs = {0, 0, 3, 6};
        Carrot.Point previous = null;
        for (double px = 0; px <= 1.0; px += 0.05) {
            double[] xs = {px, 1, 1, 1};
            Carrot.Point p = Carrot.along(xs, zs, new double[]{1, 1, 1, 1}, NO_STOPS, 2);
            if (previous != null) {
                assertTrue(Math.hypot(p.x() - previous.x(), p.z() - previous.z()) < 0.2,
                        "carrot jumped at px=" + px);
            }
            previous = p;
        }
    }

    @Test
    void stopsAtACornerThatIsNotSafeToCut() {
        double[] xs = {0, 2, 2, 2};
        double[] zs = {0, 0, 5, 9};

        Carrot.Point p = Carrot.along(xs, zs, new double[]{1, 1, 1, 1}, new boolean[]{false, true, false, false}, 4);

        assertEquals(2, p.x(), 1e-9);
        assertEquals(0, p.z(), 1e-9);
        assertTrue(p.stoppedAtCorner());
    }

    @Test
    void interpolatesRouteHeightForPitch() {
        double[] xs = {0, 2, 4, 6};
        double[] ys = {1, 1, 3, 3};

        Carrot.Point p = Carrot.along(xs, new double[]{0, 0, 0, 0}, ys, NO_STOPS, 3);

        assertEquals(2, p.y(), 1e-9);
    }

    @Test
    void endsAtTheLastPointWhenTheRouteIsShorterThanTheDistance() {
        Carrot.Point p = Carrot.along(new double[]{0, 1}, new double[]{0, 0}, new double[]{1, 1}, new boolean[]{false, false}, 5);

        assertEquals(1, p.x(), 1e-9);
        assertEquals(1, p.travelled(), 1e-9);
        assertFalse(p.stoppedAtCorner());
    }
}
