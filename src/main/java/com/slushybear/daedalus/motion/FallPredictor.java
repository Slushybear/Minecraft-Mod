package com.slushybear.daedalus.motion;

/**
 * Where a falling body comes down. Vanilla keeps horizontal speed through a fall, losing only 9% a tick to air drag, so
 * a body that walks off an edge lands well beyond it: about 4.4 times its speed per tick past the edge after a 1-block
 * drop, 6 times after 3 blocks. That is a whole block at walking pace. These numbers let the walker take an edge slowly
 * enough to land on the cell it means to, and trim the rest in the air.
 */
public final class FallPredictor {
    /** Speed to walk off an edge at, in blocks per tick, when there is no room past the landing to overshoot into. */
    public static final double EDGE_SPEED = 0.1;

    private static final double AIR_DRAG = 0.91;
    private static final double GRAVITY = 0.08;
    private static final double VERTICAL_DRAG = 0.98;
    private static final int MAX_TICKS = 100;

    private FallPredictor() {}

    /**
     * How far the body still travels sideways before landing, per block-per-tick of its current horizontal speed, with
     * no input. Multiply by the horizontal velocity to get the landing point.
     *
     * @param y the feet's height now
     * @param vy vertical speed now, in blocks per tick (negative is down)
     * @param landingY the height of the top it lands on
     */
    public static double carry(double y, double vy, double landingY) {
        double travelled = 0;
        double share = 1;
        for (int tick = 0; tick < MAX_TICKS && y > landingY; tick++) {
            share *= AIR_DRAG;
            travelled += share;
            y += vy;
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
        }
        return travelled;
    }
}
