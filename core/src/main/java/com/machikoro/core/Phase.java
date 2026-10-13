package com.machikoro.core;

/**
 * What the game is waiting for.
 */
public enum Phase {
    /** Active player must roll the dice. */
    ROLL,
    /** Active player owns Radio Tower and decides whether to reroll or keep the result. */
    REROLL_DECISION,
    /** Active player must choose a target for TV Station or Business Center. */
    AWAITING_TARGET,
    /** Active player may build one card or pass. */
    BUILD,
    /** Someone has built all landmarks. */
    GAME_OVER
}
