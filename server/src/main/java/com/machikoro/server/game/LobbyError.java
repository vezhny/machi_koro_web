package com.machikoro.server.game;

/**
 * Errors outside the game rules: lobby, authentication, malformed requests.
 */
public enum LobbyError {
    GAME_NOT_FOUND(404),
    GAME_FULL(409),
    GAME_ALREADY_STARTED(409),
    GAME_NOT_STARTED(409),
    NOT_ENOUGH_PLAYERS(409),
    NOT_HOST(403),
    WRONG_GAME(403),
    INVALID_TOKEN(401),
    BAD_REQUEST(400);

    private final int httpStatus;

    LobbyError(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
