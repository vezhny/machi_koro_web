package com.machikoro.core.engine;

import com.machikoro.core.GameState;
import com.machikoro.core.event.GameEvent;
import java.util.List;
import java.util.Objects;

/**
 * New state after a command and the events that led to it, in order.
 */
public record GameResult(GameState state, List<GameEvent> events) {

    public GameResult {
        Objects.requireNonNull(state, "state");
        events = List.copyOf(events);
    }
}
