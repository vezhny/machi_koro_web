package com.machikoro.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates the initial state of a game.
 */
public final class GameSetup {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;
    public static final int STARTING_COINS = 3;

    private GameSetup() {
    }

    /**
     * Each player starts with 3 coins, one Wheat Field and one Bakery. Starting cards are not
     * taken from the bank (see docs/OPEN_QUESTIONS.md). The first player in the list moves first.
     *
     * @throws IllegalArgumentException for fewer than 2 or more than 4 players, or duplicate ids
     */
    public static GameState newGame(String gameId, List<PlayerInfo> players) {
        if (players.size() < MIN_PLAYERS || players.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "Expected " + MIN_PLAYERS + "-" + MAX_PLAYERS + " players, got " + players.size());
        }
        List<PlayerState> states = players.stream()
                .map(GameSetup::startingPlayer)
                .toList();
        return new GameState(gameId, 0, states, 0, Phase.ROLL, initialSupply(), TurnContext.start(), null);
    }

    private static PlayerState startingPlayer(PlayerInfo info) {
        return new PlayerState(
                info.id(),
                info.name(),
                STARTING_COINS,
                Map.of(Establishment.WHEAT_FIELD, 1, Establishment.BAKERY, 1),
                Set.of());
    }

    private static Map<Establishment, Integer> initialSupply() {
        Map<Establishment, Integer> supply = new EnumMap<>(Establishment.class);
        for (Establishment establishment : Establishment.values()) {
            supply.put(establishment, establishment.supplyCount());
        }
        return supply;
    }
}
