package com.machikoro.server.api;

import com.machikoro.server.game.GameStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * REST payloads of the lobby. See docs/PROTOCOL.md.
 */
public final class LobbyDtos {

    private LobbyDtos() {
    }

    public record NameRequest(@NotBlank @Size(max = 24) String name) {
    }

    /** Credentials of a seat. The token is shown only once, to the player who took the seat. */
    public record JoinResponse(String gameId, String playerId, String playerToken) {
    }

    public record PlayerSummary(String id, String name) {
    }

    /** Open lobby in the list of games. */
    public record LobbySummary(String gameId, String hostName, int playerCount, int maxPlayers) {
    }

    /** Lobby state; also pushed to {@code /topic/games/{id}/lobby} on every change. */
    public record LobbyView(String gameId, GameStatus status, String hostPlayerId, List<PlayerSummary> players) {
    }

    /** Everything needed to (re)open a game screen. {@code state} is {@code null} in the lobby. */
    public record GameDetails(String gameId, GameStatus status, String hostPlayerId, List<PlayerSummary> players,
                              GameView state) {
    }

    public record ErrorMessage(String code, String message) {
    }
}
