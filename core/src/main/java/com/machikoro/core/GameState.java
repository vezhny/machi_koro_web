package com.machikoro.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of a whole game.
 *
 * @param version          incremented by every successfully applied command
 * @param players          in seating (turn) order
 * @param activePlayerIndex index into {@code players} of the player whose turn it is
 * @param supply           establishments left in the bank; absent key means none left
 * @param winnerId         id of the winner, {@code null} until the game is over
 */
public record GameState(
        String gameId,
        long version,
        List<PlayerState> players,
        int activePlayerIndex,
        Phase phase,
        Map<Establishment, Integer> supply,
        TurnContext turn,
        String winnerId) {

    public GameState {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(turn, "turn");
        if (version < 0) {
            throw new IllegalArgumentException("Version must not be negative: " + version);
        }
        players = List.copyOf(players);
        if (players.isEmpty()) {
            throw new IllegalArgumentException("Game must have players");
        }
        if (new HashSet<>(players.stream().map(PlayerState::id).toList()).size() != players.size()) {
            throw new IllegalArgumentException("Player ids must be unique");
        }
        if (activePlayerIndex < 0 || activePlayerIndex >= players.size()) {
            throw new IllegalArgumentException("Active player index out of range: " + activePlayerIndex);
        }
        supply = EnumCollections.countsCopy(Establishment.class, supply);
        if ((phase == Phase.GAME_OVER) != (winnerId != null)) {
            throw new IllegalArgumentException("Winner must be set exactly when the game is over");
        }
    }

    public PlayerState activePlayer() {
        return players.get(activePlayerIndex);
    }

    public Optional<PlayerState> findPlayer(String playerId) {
        return players.stream().filter(p -> p.id().equals(playerId)).findFirst();
    }

    /**
     * @throws IllegalArgumentException if no player has this id
     */
    public PlayerState player(String playerId) {
        return findPlayer(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + playerId));
    }

    public int supplyOf(Establishment establishment) {
        return supply.getOrDefault(establishment, 0);
    }

    /** Replaces the player with the same id. */
    public GameState withPlayer(PlayerState updated) {
        List<PlayerState> newPlayers = new ArrayList<>(players);
        int index = indexOf(updated.id());
        newPlayers.set(index, updated);
        return new GameState(gameId, version, newPlayers, activePlayerIndex, phase, supply, turn, winnerId);
    }

    public GameState withPhase(Phase newPhase) {
        return new GameState(gameId, version, players, activePlayerIndex, newPhase, supply, turn, winnerId);
    }

    public GameState withTurn(TurnContext newTurn) {
        return new GameState(gameId, version, players, activePlayerIndex, phase, supply, newTurn, winnerId);
    }

    public GameState withActivePlayerIndex(int index) {
        return new GameState(gameId, version, players, index, phase, supply, turn, winnerId);
    }

    public GameState withSupply(Map<Establishment, Integer> newSupply) {
        return new GameState(gameId, version, players, activePlayerIndex, phase, newSupply, turn, winnerId);
    }

    public GameState withWinner(String playerId) {
        return new GameState(gameId, version, players, activePlayerIndex, Phase.GAME_OVER, supply, turn, playerId);
    }

    public GameState nextVersion() {
        return new GameState(gameId, version + 1, players, activePlayerIndex, phase, supply, turn, winnerId);
    }

    private int indexOf(String playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id().equals(playerId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown player: " + playerId);
    }
}
