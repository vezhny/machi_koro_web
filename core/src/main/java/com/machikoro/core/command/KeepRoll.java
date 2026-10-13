package com.machikoro.core.command;

/**
 * Radio Tower: keep the current roll.
 */
public record KeepRoll(String playerId, long expectedVersion) implements Command {
}
