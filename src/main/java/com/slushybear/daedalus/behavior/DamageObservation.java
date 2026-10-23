package com.slushybear.daedalus.behavior;

import java.util.List;

/**
 * One observed loss of health. The attacker is only filled in when the game itself reported a source, so an empty
 * attacker means "unknown", never "the nearest mob". Nearby hostiles are context for a human reading the log.
 */
public record DamageObservation(
        long gameTime,
        float amount,
        float healthAfter,
        String category,
        String attacker,
        String directSource,
        Confidence confidence,
        List<String> nearbyHostiles
) {
    public enum Confidence {
        /** The game reported who or what caused it. */
        REPORTED,
        /** Health dropped and nothing identified the cause. */
        UNKNOWN
    }
}
