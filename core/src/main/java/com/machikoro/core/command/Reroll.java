package com.machikoro.core.command;

/**
 * Radio Tower: discard the roll and roll again, choosing the number of dice anew.
 */
public record Reroll(String playerId, long expectedVersion, int diceCount) implements Command {
}
