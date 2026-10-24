package com.slushybear.daedalus.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GaitTest {
    @Test
    void headingStraightAheadIsFullSpeedForwardAndMaySprint() {
        Gait.Drive drive = Gait.drive(40, 40, 1);

        assertEquals(1, drive.forward(), 1e-9);
        assertEquals(0, drive.left(), 1e-9);
        assertTrue(drive.sprintOk());
    }

    @Test
    void aGentleCornerKeepsMovingAtFullSpeedWhileTheViewCatchesUp() {
        Gait.Drive drive = Gait.drive(0, 25, 1);

        assertTrue(Math.hypot(drive.forward(), drive.left()) > 0.99, "should still be at full speed");
        assertFalse(drive.sprintOk());
    }

    @Test
    void aSharpTurnSlowsRightDown() {
        Gait.Drive drive = Gait.drive(0, 80, 1);

        assertTrue(Math.hypot(drive.forward(), drive.left()) < 0.6);
        assertTrue(drive.forward() > 0, "still easing forward, not stopped");
    }

    @Test
    void waitsForTheViewWhenTheHeadingIsBehind() {
        Gait.Drive drive = Gait.drive(0, 135, 1);

        assertEquals(0, drive.forward(), 1e-9);
        assertEquals(0, drive.left(), 1e-9);
    }

    @Test
    void neverMovesFasterThanAskedAndSlowerSpeedsScaleDown() {
        for (double heading = -180; heading <= 180; heading += 10) {
            Gait.Drive full = Gait.drive(0, heading, 1);
            Gait.Drive half = Gait.drive(0, heading, 0.5);
            assertTrue(Math.hypot(full.forward(), full.left()) <= 1.0001);
            assertEquals(Math.hypot(full.forward(), full.left()) / 2, Math.hypot(half.forward(), half.left()), 1e-9);
            assertFalse(half.sprintOk(), "no sprinting when asked to go slow");
        }
    }

    @Test
    void movesTowardTheHeadingOnTheGround() {
        // View facing +z (yaw 0), heading toward -x (yaw 90): that is to the right of the view.
        Gait.Drive drive = Gait.drive(0, 40, 1);
        double dx = drive.left() * Math.cos(Math.toRadians(0)) + drive.forward() * -Math.sin(Math.toRadians(0));
        double dz = drive.left() * Math.sin(Math.toRadians(0)) + drive.forward() * Math.cos(Math.toRadians(0));

        double wantedYaw = Gait.yawTo(dx, dz);

        assertEquals(40, wantedYaw, 1e-6);
    }

    @Test
    void localInputPushesTheWayAskedWhateverTheView() {
        for (double view = -180; view < 180; view += 30) {
            for (double angle = 0; angle < 360; angle += 45) {
                double dx = -Math.sin(Math.toRadians(angle));
                double dz = Math.cos(Math.toRadians(angle));
                double[] input = Gait.local(view, dx, dz);
                // Turn the input back into a world direction the way vanilla does: forward (-sin, cos), left (cos, sin).
                double yaw = Math.toRadians(view);
                double wx = input[0] * -Math.sin(yaw) + input[1] * Math.cos(yaw);
                double wz = input[0] * Math.cos(yaw) + input[1] * Math.sin(yaw);
                assertEquals(dx, wx, 1e-9, "view " + view + " angle " + angle);
                assertEquals(dz, wz, 1e-9, "view " + view + " angle " + angle);
            }
        }
    }

    @Test
    void yawFollowsTheGameConvention() {
        assertEquals(0, Gait.yawTo(0, 1), 1e-9);
        assertEquals(90, Gait.yawTo(-1, 0), 1e-9);
        assertEquals(-90, Gait.yawTo(1, 0), 1e-9);
        assertEquals(180, Math.abs(Gait.yawTo(0, -1)), 1e-9);
    }
}
