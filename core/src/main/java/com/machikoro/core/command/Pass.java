package com.machikoro.core.command;

/**
 * Build nothing and end the turn.
 */
public record Pass(String playerId, long expectedVersion) implements Command {
}
