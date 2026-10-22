package com.slushybear.daedalus.automation;

/**
 * One instruction in a macro. The meaning of target and amount depends on the type; see ActionType.
 * Steps are only ever created by DefinitionValidator, so the fields are already checked.
 */
public record Step(ActionType type, String target, int amount) {
    public String describe() {
        return switch (type) {
            case WAIT -> "wait " + amount + " ticks";
            case HOLD -> "hold " + target + " for " + amount + " ticks";
            case SWAP_SLOT -> "select slot " + amount;
            case CHAT -> "chat \"" + target + "\"";
            case HARVEST_CROPS -> "harvest " + target + " within " + amount;
            case REPLANT_CROPS -> "replant " + target + " within " + amount;
            case GOTO -> "walk to " + target;
            case MINE -> "mine " + target + " within " + amount;
        };
    }
}
