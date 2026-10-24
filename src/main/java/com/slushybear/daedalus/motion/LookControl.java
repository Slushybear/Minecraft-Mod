package com.slushybear.daedalus.motion;

/**
 * Turns the view toward a target the way a hand on a mouse does: it eases in, turns fastest in the middle, and eases out
 * as it lines up. It keeps its own turning speed from tick to tick, so a target that moves a little moves the view a
 * little, and a target that stays still lets the view settle and stay put. There is no randomness anywhere in it.
 *
 * <p>Each tick the wanted speed is proportional to how far there is left to turn, capped at {@link #MAX_RATE}. The real
 * speed then moves toward the wanted speed by at most {@link #ACCEL} to speed up and {@link #DECEL} to slow down, which
 * gives the gentle start and the soft landing.
 *
 * <p>Angles are in degrees. Yaw is kept as a plain number that may drift past 360 like the game's own, and every
 * difference is wrapped, so turning always goes the short way round.
 */
public final class LookControl {
    /** Fastest turn, in degrees per tick: a quick glance, not a flick. */
    public static final double MAX_RATE = 16;
    /** How quickly the turn speeds up, in degrees per tick per tick. */
    public static final double ACCEL = 3.2;
    /** How quickly it slows down. Larger than ACCEL so the view lands on the target without overshooting. */
    public static final double DECEL = 5.0;
    /** Share of the remaining angle the wanted speed is set to, each tick. */
    private static final double GAIN = 0.2;
    /** Close enough to call it lined up. */
    private static final double SETTLED_ANGLE = 0.05;
    private static final double SETTLED_SPEED = 0.05;

    /** How briskly to turn. QUICK is for moves that need an accurate heading in a hurry: jumps and swimming. */
    public enum Pace {
        NORMAL(MAX_RATE, ACCEL, DECEL, GAIN),
        QUICK(40, 12, 14, 0.5);

        final double maxRate;
        final double accel;
        final double decel;
        final double gain;

        Pace(double maxRate, double accel, double decel, double gain) {
            this.maxRate = maxRate;
            this.accel = accel;
            this.decel = decel;
            this.gain = gain;
        }
    }

    private double yawSpeed;
    private double pitchSpeed;

    /** Moves the view one tick toward the target. Returns {yaw, pitch}. */
    public double[] step(double yaw, double pitch, double targetYaw, double targetPitch) {
        return step(yaw, pitch, targetYaw, targetPitch, Pace.NORMAL);
    }

    /** Moves the view one tick toward the target at the given pace. Returns {yaw, pitch}. */
    public double[] step(double yaw, double pitch, double targetYaw, double targetPitch, Pace pace) {
        double yawError = wrap(targetYaw - yaw);
        double pitchError = targetPitch - pitch;
        yawSpeed = approach(yawSpeed, yawError, pace);
        pitchSpeed = approach(pitchSpeed, pitchError, pace);

        double newYaw = yaw + yawSpeed;
        double newPitch = pitch + pitchSpeed;
        if (Math.abs(wrap(targetYaw - newYaw)) < SETTLED_ANGLE && Math.abs(yawSpeed) < SETTLED_SPEED) {
            newYaw = yaw + yawError;
            yawSpeed = 0;
        }
        if (Math.abs(targetPitch - newPitch) < SETTLED_ANGLE && Math.abs(pitchSpeed) < SETTLED_SPEED) {
            newPitch = targetPitch;
            pitchSpeed = 0;
        }
        return new double[]{newYaw, newPitch};
    }

    /** Puts the view straight on the target and stops turning. For jumps that need an exact heading. */
    public double[] snap(double yaw, double targetYaw, double targetPitch) {
        yawSpeed = 0;
        pitchSpeed = 0;
        return new double[]{yaw + wrap(targetYaw - yaw), targetPitch};
    }

    /** Forget the turning speed, for when something else turned the view (a teleport, a respawn). */
    public void reset() {
        yawSpeed = 0;
        pitchSpeed = 0;
    }

    private static double approach(double speed, double error, Pace pace) {
        double wanted = Math.max(-pace.maxRate, Math.min(pace.maxRate, error * pace.gain));
        double change = wanted - speed;
        // Slowing down means the wanted speed is smaller in size than the current one.
        double limit = Math.abs(wanted) < Math.abs(speed) || wanted * speed < 0 ? pace.decel : pace.accel;
        return speed + Math.max(-limit, Math.min(limit, change));
    }

    /** An angle difference wrapped to [-180, 180). */
    public static double wrap(double degrees) {
        double d = degrees % 360;
        if (d >= 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }
}
