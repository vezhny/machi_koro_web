package com.machikoro.server.game;

public final class LobbyException extends RuntimeException {

    private final LobbyError code;

    public LobbyException(LobbyError code, String message) {
        super(message);
        this.code = code;
    }

    public LobbyError code() {
        return code;
    }
}
