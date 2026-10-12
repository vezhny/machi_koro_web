package com.machikoro.core.event;

import com.machikoro.core.Establishment;

/**
 * Business Center: {@code playerId} gave {@code given} to {@code targetPlayerId} and took {@code taken}.
 */
public record EstablishmentsSwapped(String playerId, String targetPlayerId, Establishment given, Establishment taken)
        implements GameEvent {
}
