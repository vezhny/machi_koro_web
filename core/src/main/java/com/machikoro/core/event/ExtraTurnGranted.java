package com.machikoro.core.event;

/**
 * Amusement Park: the player rolled doubles and takes another turn.
 */
public record ExtraTurnGranted(String playerId) implements GameEvent {
}
