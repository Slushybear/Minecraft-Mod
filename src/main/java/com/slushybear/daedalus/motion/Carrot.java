package com.slushybear.daedalus.motion;

/**
 * A point a fixed distance ahead along the route, the thing the eyes and the feet follow. Because it is measured along
 * the line from where the player actually is, through the cells of the route, it slides smoothly as the player moves
 * and never jumps when the walker moves on to the next cell. Aiming at the farthest cell in range, or at the centre of
 * the next cell, does jump, and that is what makes a view twitch.
 */
public final class Carrot {
    /** Where the carrot is: ground position, route height, and how far along the line it is. */
    public record Point(double x, double z, double y, double travelled, boolean stoppedAtCorner) {}

    private Carrot() {}

    /**
     * @param xs ground x of the line, starting at the player
     * @param zs ground z of the line
     * @param ys route height at each point, for pitch
     * @param stop whether the carrot must not go past a point: a corner that is not safe to cut, or where the next move
     *             needs the body at an exact spot. stop[0] is ignored
     * @param distance how far ahead to put it, in blocks
     */
    public static Point along(double[] xs, double[] zs, double[] ys, boolean[] stop, double distance) {
        double travelled = 0;
        for (int i = 1; i < xs.length; i++) {
            double length = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            if (travelled + length >= distance && length > 1e-9) {
                double t = (distance - travelled) / length;
                return new Point(
                        xs[i - 1] + (xs[i] - xs[i - 1]) * t,
                        zs[i - 1] + (zs[i] - zs[i - 1]) * t,
                        ys[i - 1] + (ys[i] - ys[i - 1]) * t,
                        distance, false);
            }
            travelled += length;
            if (stop[i]) {
                return new Point(xs[i], zs[i], ys[i], travelled, true);
            }
        }
        int last = xs.length - 1;
        return new Point(xs[last], zs[last], ys[last], travelled, false);
    }
}
