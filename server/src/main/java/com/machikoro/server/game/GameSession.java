package com.machikoro.server.game;

import com.machikoro.core.GameState;
import com.machikoro.core.PlayerInfo;
import com.machikoro.core.command.Command;
import com.machikoro.core.engine.RandomSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One game from lobby to the end. All mutable fields are guarded by {@link #lock}: commands of a
 * game are applied strictly one at a time.
 */
final class GameSession {

    record LoggedCommand(Instant at, Command command) {
    }

    final String id;
    final String hostPlayerId;
    final long seed;
    final RandomSource dice;
    final ReentrantLock lock = new ReentrantLock();

    /** Seating order. */
    final List<PlayerInfo> seats = new ArrayList<>();
    /** {@code null} while in the lobby. */
    GameState state;
    /** Accepted commands, for debugging and future replays from {@link #seed}. */
    final List<LoggedCommand> log = new ArrayList<>();

    GameSession(String id, String hostPlayerId, long seed, RandomSource dice) {
        this.id = id;
        this.hostPlayerId = hostPlayerId;
        this.seed = seed;
        this.dice = dice;
    }

    GameStatus status() {
        if (state == null) {
            return GameStatus.LOBBY;
        }
        return state.winnerId() == null ? GameStatus.IN_PROGRESS : GameStatus.FINISHED;
    }
}
