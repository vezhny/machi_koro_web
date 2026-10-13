package com.machikoro.core.event;

import com.machikoro.core.Establishment;

/**
 * The active player must choose a target for {@code card} (TV Station or Business Center).
 */
public record TargetRequired(String playerId, Establishment card) implements GameEvent {
}
