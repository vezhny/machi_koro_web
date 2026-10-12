package com.machikoro.server.api;

import com.machikoro.core.engine.GameResult;
import com.machikoro.core.event.GameEvent;
import java.util.List;

/**
 * Broadcast to {@code /topic/games/{id}} after every accepted command.
 */
public record GameUpdate(GameView state, List<GameEvent> events) {

    public static GameUpdate from(GameResult result) {
        return new GameUpdate(GameView.from(result.state()), result.events());
    }
}
