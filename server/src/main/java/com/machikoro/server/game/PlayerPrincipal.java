package com.machikoro.server.game;

import java.security.Principal;

/**
 * Authenticated player of one game. {@link #getName()} is the player id, so user destinations
 * ({@code /user/queue/errors}) resolve per player.
 */
public record PlayerPrincipal(String playerId, String gameId) implements Principal {

    @Override
    public String getName() {
        return playerId;
    }
}
