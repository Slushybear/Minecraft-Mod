package com.slushybear.daedalus.module;

/** The sidebar tabs, in sidebar order. Macros holds the macro editor instead of modules. */
public enum Category {
    COMBAT("Combat"),
    MINING("Mining"),
    FARMING("Farming"),
    MACROS("Macros"),
    MISC("Miscellaneous"),
    VISUALS("Visuals");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
