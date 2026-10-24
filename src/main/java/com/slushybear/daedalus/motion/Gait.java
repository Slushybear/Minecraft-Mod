package com.slushybear.daedalus.motion;

/**
 * Splits "where the feet want to go" from "where the eyes are looking". The view leads and turns smoothly, and the feet
 * go toward their own heading, so a gentle corner is taken while still turning: the body moves forward and a little to
 * the side to follow the heading, at close to full speed. When the view is far from the heading (a sharp turn, or a
 * turn that has only just started) the body slows to a stroll, and stops pushing at all if it would have to walk
 * sideways or backwards, until the view has come round.
 */
public final class Gait {
    /** Heading within this many degrees of the view is walked at full speed, and may sprint within {@link #SPRINT_ANGLE}. */
    public static final double FULL_SPEED_ANGLE = 30;
    public static final double SPRINT_ANGLE = 20;
    /** At or past this many degrees from the view the body waits for the view to come round. */
    public static final double WAIT_ANGLE = 100;
    /** Slowest walking speed while still moving, as a share of full speed. */
    private static final double SLOWEST = 0.35;

    /** Move input: `forward` and `left` between -1 and 1, and the length of the pair is at most 1. */
    public record Drive(double forward, double left, boolean sprintOk) {}

    private Gait() {}

    /**
     * @param viewYaw where the view faces
     * @param headingYaw the direction the feet should travel, in the same yaw convention
     * @param speed 0 to 1: how fast to go, before looking at how well the view lines up
     */
    public static Drive drive(double viewYaw, double headingYaw, double speed) {
        double angle = LookControl.wrap(headingYaw - viewYaw);
        double size = Math.abs(angle);
        if (size >= WAIT_ANGLE) {
            return new Drive(0, 0, false);
        }
        double alignment = size <= FULL_SPEED_ANGLE
                ? 1
                : 1 - (1 - SLOWEST) * (size - FULL_SPEED_ANGLE) / (WAIT_ANGLE - FULL_SPEED_ANGLE);
        double radians = Math.toRadians(angle);
        double amount = speed * alignment;
        // Facing yaw f gives forward (-sin f, cos f) and left (cos f, sin f), so a heading d degrees to the right of the
        // view is cos(d) forward and sin(d) to the right.
        return new Drive(Math.cos(radians) * amount, -Math.sin(radians) * amount, size <= SPRINT_ANGLE && speed >= 1);
    }

    /** The yaw, in game degrees, for a direction on the ground. Forward at yaw 0 is +z, and yaw 90 faces -x. */
    public static double yawTo(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }
}
