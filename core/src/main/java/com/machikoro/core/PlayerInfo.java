package com.machikoro.core;

import java.util.Objects;

/**
 * A player joining a new game.
 */
public record PlayerInfo(String id, String name) {

    public PlayerInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}
