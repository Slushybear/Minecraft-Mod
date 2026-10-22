package com.slushybear.daedalus.automation;

import java.util.Optional;

/** The fixed set of things a macro step can do. Players can only combine these, never run custom code. */
public enum ActionType {
    /** Pause the macro. Target is empty, amount is ticks. */
    WAIT,
    /** Hold one key for a number of ticks. Target is a HeldKey, amount is ticks. */
    HOLD,
    /** Select a hotbar slot. Amount is 1-9. */
    SWAP_SLOT,
    /** Send a chat message. Target is the text. Commands are refused. */
    CHAT,
    /** Harvest mature crops in a circle. Target is a CropType, amount is radius. */
    HARVEST_CROPS,
    /** Plant seeds on empty farmland in a circle. Target is a CropType, amount is radius. */
    REPLANT_CROPS,
    /** Walk to a block position with the pathfinder. Target is "x y z". */
    GOTO,
    /** Break the nearest matching blocks in reach. Target is a MineTarget, amount is radius. */
    MINE;

    public static Optional<ActionType> byId(String id) {
        for (ActionType type : values()) {
            if (type.name().equalsIgnoreCase(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
