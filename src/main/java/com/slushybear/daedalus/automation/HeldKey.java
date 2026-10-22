package com.slushybear.daedalus.automation;

import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

/** Keys a HOLD step may press. Mapped to the game's own key bindings. */
public enum HeldKey {
    FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT, ATTACK, USE;

    public static Optional<HeldKey> byId(String id) {
        for (HeldKey key : values()) {
            if (key.name().equalsIgnoreCase(id)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    public KeyMapping mapping(Options options) {
        return switch (this) {
            case FORWARD -> options.keyUp;
            case BACK -> options.keyDown;
            case LEFT -> options.keyLeft;
            case RIGHT -> options.keyRight;
            case JUMP -> options.keyJump;
            case SNEAK -> options.keyShift;
            case SPRINT -> options.keySprint;
            case ATTACK -> options.keyAttack;
            case USE -> options.keyUse;
        };
    }
}
