package com.machikoro.core.engine;

/**
 * Machine-readable reason a command was rejected.
 */
public enum RuleViolation {
    GAME_OVER,
    UNKNOWN_PLAYER,
    NOT_YOUR_TURN,
    STALE_VERSION,
    /** Command is not allowed in the current phase (includes a second purchase per turn). */
    WRONG_PHASE,
    INVALID_DICE_COUNT,
    TRAIN_STATION_REQUIRED,
    /** Target player is missing, unknown or the active player themself. */
    INVALID_TARGET,
    /** Business Center: a card to give or take is not owned or is purple. */
    INVALID_SWAP,
    NOT_ENOUGH_COINS,
    SUPPLY_EMPTY,
    PURPLE_ALREADY_OWNED,
    LANDMARK_ALREADY_BUILT
}
