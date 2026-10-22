package com.slushybear.daedalus.automation;

/** Hard caps compiled into the mod. Remote definitions can tighten these but never raise them. */
public final class Limits {
    public static final int MAX_MACROS = 10;
    public static final int MAX_STEPS = 32;
    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_CHAT_LENGTH = 100;

    /** Vanilla block reach is about 4.5 to 6 blocks, so larger radii would fail on the server anyway. */
    public static final int MAX_RADIUS = 6;
    public static final int MAX_WAIT_TICKS = 1200;
    public static final int MAX_HOLD_TICKS = 200;
    public static final int MIN_INTERVAL_TICKS = 20;
    public static final int MAX_INTERVAL_TICKS = 1200;

    /** Caps how many instant steps all macros together run in one client tick. */
    public static final int MAX_INSTANT_STEPS_PER_TICK = 8;
    /** Caps how many block interactions one harvest or replant step sends. */
    public static final int MAX_BLOCK_ACTIONS_PER_STEP = 8;

    public static final int KEY_SLOTS = 10;

    /** Caps how many nodes one path search may expand, so a bad target cannot freeze the client. */
    public static final int MAX_PATH_NODES = 20000;
    /** Caps how far a GOTO can reach, measured in blocks on the flat plane. */
    public static final int MAX_PATH_DISTANCE = 96;
    /** Caps how far a GOTO can differ in height, which also bounds how much terrain is copied for the search. */
    public static final int MAX_PATH_HEIGHT = 32;
    /** How long a macro waits for the background search before giving up. */
    public static final int PLAN_TIMEOUT_TICKS = 200;
    public static final int GOTO_TIMEOUT_TICKS = 1200;

    public static final int MAX_MINE_RADIUS = 4;
    public static final int MINE_TIMEOUT_TICKS = 600;
    public static final int MAX_BLOCKS_PER_MINE = 16;

    private Limits() {}
}
