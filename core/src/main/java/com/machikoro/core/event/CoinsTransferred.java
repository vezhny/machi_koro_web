package com.machikoro.core.event;

import com.machikoro.core.Establishment;

/**
 * Coins paid from one player to another because of {@code card} (red cards, Stadium, TV Station).
 */
public record CoinsTransferred(String fromPlayerId, String toPlayerId, Establishment card, int amount)
        implements GameEvent {
}
