package com.machikoro.core.command;

/**
 * A move requested by a player.
 */
public sealed interface Command permits RollDice, Reroll, KeepRoll, ChooseTarget, Build, Pass {

    String playerId();

    /** Version of the game state the player saw when issuing the command. */
    long expectedVersion();
}
