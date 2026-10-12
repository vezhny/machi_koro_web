package com.machikoro.core.command;

/**
 * Roll at the start of the turn. Two dice require Train Station.
 */
public record RollDice(String playerId, long expectedVersion, int diceCount) implements Command {
}
