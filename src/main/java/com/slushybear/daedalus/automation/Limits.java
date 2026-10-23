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

    /** Caps how far a GOTO target can be, on the flat plane. The walk is planned one segment of nearby terrain at a time. */
    public static final int MAX_PATH_DISTANCE = 2000;
    /** Terrain copied around the player for each segment's search: this many blocks to each side, and up or down. */
    public static final int SEGMENT_RADIUS = 32;
    public static final int SEGMENT_HEIGHT = 16;
    /** How many times in a row a walk may fail and replan before the step fails. A finished segment resets the count. */
    public static final int MAX_REPLANS = 4;
    /** How long a macro waits for the background search before giving up. */
    public static final int PLAN_TIMEOUT_TICKS = 200;
    /** The whole GOTO: 10 minutes, enough to walk the full distance cap. */
    public static final int GOTO_TIMEOUT_TICKS = 12000;

    public static final int MAX_MINE_RADIUS = 4;
    public static final int MINE_TIMEOUT_TICKS = 600;
    public static final int MAX_BLOCKS_PER_MINE = 16;

    private Limits() {}
}
