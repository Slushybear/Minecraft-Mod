package com.slushybear.daedalus.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LookControlTest {
    /** Turns toward a fixed target for `ticks`, recording the yaw after each tick. */
    private static double[] turn(double startYaw, double targetYaw, int ticks) {
        LookControl look = new LookControl();
        double[] yaws = new double[ticks];
        double yaw = startYaw;
        for (int i = 0; i < ticks; i++) {
            yaw = look.step(yaw, 0, targetYaw, 0)[0];
            yaws[i] = yaw;
        }
        return yaws;
    }

    @Test
    void reachesTheTargetAndStaysThere() {
        double[] yaws = turn(0, 90, 60);

        assertEquals(90, yaws[59], 0.01);
        assertEquals(yaws[40], yaws[59], 0.01, "still turning long after arriving");
    }

    @Test
    void neverOvershootsTheTarget() {
        for (double angle : new double[]{5, 20, 45, 90, 135, 180}) {
            for (double yaw : turn(0, angle, 60)) {
                assertTrue(yaw <= angle + 0.01, "overshot " + angle + " with " + yaw);
            }
        }
    }

    @Test
    void startsGentlyAndIsFastestInTheMiddle() {
        double[] yaws = turn(0, 120, 40);
        double firstStep = yaws[0];
        double fastest = 0;
        int fastestAt = 0;
        for (int i = 1; i < yaws.length; i++) {
            double step = yaws[i] - yaws[i - 1];
            if (step > fastest) {
                fastest = step;
                fastestAt = i;
            }
        }

        assertTrue(firstStep < fastest / 3, "first tick " + firstStep + " vs fastest " + fastest);
        assertTrue(fastest <= LookControl.MAX_RATE + 0.01);
        assertTrue(fastestAt > 2 && yaws[fastestAt] > 20 && yaws[fastestAt] < 100, "fastest at " + yaws[fastestAt]);
        double lastStep = yaws[39] - yaws[38];
        assertTrue(lastStep < fastest / 4, "should have slowed to a crawl, last step " + lastStep);
    }

    @Test
    void turnSpeedChangesSmoothly() {
        double[] yaws = turn(0, 150, 50);
        for (int i = 2; i < yaws.length; i++) {
            double jerk = (yaws[i] - yaws[i - 1]) - (yaws[i - 1] - yaws[i - 2]);
            assertTrue(Math.abs(jerk) <= LookControl.DECEL + 0.01, "speed jumped by " + jerk + " at tick " + i);
        }
    }

    @Test
    void turnsTheShortWayRound() {
        double[] yaws = turn(170, -170, 30); // 20 degrees the short way, not 340 the long way

        assertEquals(190, yaws[29], 0.01);
        for (double yaw : yaws) {
            assertTrue(yaw >= 170 && yaw <= 190.01);
        }
    }

    @Test
    void aSlowlyMovingTargetIsFollowedWithoutTwitching() {
        LookControl look = new LookControl();
        double yaw = 0;
        double previousSpeed = 0;
        for (int tick = 0; tick < 100; tick++) {
            double target = tick * 0.5; // drifting 0.5 degrees a tick
            double next = look.step(yaw, 0, target, 0)[0];
            double speed = next - yaw;
            assertTrue(speed >= -0.001, "turned backwards at tick " + tick);
            assertTrue(Math.abs(speed - previousSpeed) <= LookControl.ACCEL + 0.01);
            previousSpeed = speed;
            yaw = next;
        }
        assertEquals(49.5, yaw, 3);
    }

    @Test
    void aTargetWithinAFractionOfADegreeBarelyMovesTheView() {
        LookControl look = new LookControl();
        double yaw = look.step(0, 0, 0.4, 0)[0];

        assertTrue(yaw <= 0.4);
    }

    @Test
    void theQuickPaceStillEasesButGetsThereInAFewTicks() {
        for (double angle : new double[]{10, 45, 90, 170}) {
            LookControl look = new LookControl();
            double yaw = 0;
            double previous = 0;
            int arrived = -1;
            for (int tick = 0; tick < 30; tick++) {
                double next = look.step(yaw, 0, angle, 0, LookControl.Pace.QUICK)[0];
                assertTrue(next <= angle + 0.01, "overshot " + angle);
                assertTrue(Math.abs((next - yaw) - previous) <= 14.01, "speed jumped too hard");
                previous = next - yaw;
                yaw = next;
                if (arrived < 0 && Math.abs(angle - yaw) < 0.5) {
                    arrived = tick;
                }
            }
            assertTrue(arrived >= 0 && arrived <= 12, "took " + arrived + " ticks for " + angle + " degrees");
            assertTrue(arrived >= 1 || angle < 15, "a big turn should not be a single-tick snap");
        }
    }

    @Test
    void theQuickPaceIsFasterThanTheNormalOne() {
        LookControl normal = new LookControl();
        LookControl quick = new LookControl();
        double a = 0;
        double b = 0;
        for (int tick = 0; tick < 5; tick++) {
            a = normal.step(a, 0, 90, 0)[0];
            b = quick.step(b, 0, 90, 0, LookControl.Pace.QUICK)[0];
        }

        assertTrue(b > a * 1.5, "quick " + b + " vs normal " + a);
    }

    @Test
    void snapGoesStraightToTheTargetAndStopsTurning() {
        LookControl look = new LookControl();
        look.step(0, 0, 90, 0); // build up some speed

        double[] snapped = look.snap(10, 80, 5);

        assertEquals(80, snapped[0], 1e-9);
        assertEquals(5, snapped[1], 1e-9);
        assertEquals(80, look.step(80, 5, 80, 5)[0], 1e-9);
    }
}
