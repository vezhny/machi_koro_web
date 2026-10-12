package com.machikoro.server.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Secret per-seat tokens. A token identifies exactly one player in exactly one game.
 */
@Component
public class PlayerTokens {

    private final Map<String, PlayerPrincipal> tokens = new ConcurrentHashMap<>();

    String issue(String gameId, String playerId) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new PlayerPrincipal(playerId, gameId));
        return token;
    }

    public Optional<PlayerPrincipal> authenticate(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(tokens.get(token));
    }
}
