package com.slushybear.daedalus.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FallPredictorTest {
    /** The body tips off an edge once its centre is 0.8 past the takeoff block's centre (half a block plus half its width). */
    private static final double TIP_OFF = 0.8;
    /** Vanilla walking speed in blocks per tick. */
    private static final double WALK = 4.317 / 20;

    /** Landing position along the drop, measured from the takeoff block's centre. The target cell spans 0.5 to 1.5. */
    private static double landing(double speed, int drop) {
        return TIP_OFF + speed * FallPredictor.carry(0, 0, -drop);
    }

    @Test
    void walkingOffAnEdgeAtFullPaceOvershootsTheCellBelow() {
        // This is the bug the slow edge fixes: at a walk the body lands past the cell it was meant to drop into.
        for (int drop = 1; drop <= 3; drop++) {
            assertTrue(landing(WALK, drop) > 1.5, drop + "-block drop at walking pace landed at " + landing(WALK, drop));
        }
    }

    @Test
    void takingTheEdgeSlowlyLandsInTheCell() {
        for (int drop = 1; drop <= 3; drop++) {
            double x = landing(FallPredictor.EDGE_SPEED, drop);
            assertTrue(x > 0.5 + 0.3 && x < 1.5, drop + "-block drop at edge speed landed at " + x);
        }
    }

    @Test
    void carryGrowsWithTheFallButLevelsOff() {
        double one = FallPredictor.carry(0, 0, -1);
        double three = FallPredictor.carry(0, 0, -3);
        double twenty = FallPredictor.carry(0, 0, -20);

        assertEquals(4.4, one, 0.6);
        assertTrue(three > one && twenty > three);
        assertTrue(twenty < 1 / (1 - 0.91), "air drag caps how far momentum can carry");
    }

    @Test
    void alreadyOnTheLandingMeansNoMoreTravel() {
        assertEquals(0, FallPredictor.carry(5, -0.3, 5), 1e-9);
    }
}
