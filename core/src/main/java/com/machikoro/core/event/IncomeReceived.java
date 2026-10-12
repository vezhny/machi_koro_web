package com.machikoro.core.event;

import com.machikoro.core.Establishment;

/**
 * Income from the bank for all copies of {@code card} the player owns.
 */
public record IncomeReceived(String playerId, Establishment card, int amount) implements GameEvent {
}
