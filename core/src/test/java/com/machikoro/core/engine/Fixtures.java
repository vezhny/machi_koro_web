package com.machikoro.core.engine;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameSetup;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.Phase;
import com.machikoro.core.PlayerInfo;
import com.machikoro.core.PlayerState;
import com.machikoro.core.TurnContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Builders for hand-crafted game states.
 */
final class Fixtures {

    private Fixtures() {
    }

    static PlayerState player(String id, int coins, Map<Establishment, Integer> cards, Landmark... landmarks) {
        return new PlayerState(id, id.toUpperCase(), coins, cards, Set.of(landmarks));
    }

    /** First player is active, phase ROLL, full bank. */
    static GameState game(PlayerState... players) {
        return new GameState("g", 0, List.of(players), 0, Phase.ROLL, fullSupply(), TurnContext.start(), null);
    }

    static Map<Establishment, Integer> fullSupply() {
        Map<Establishment, Integer> supply = new EnumMap<>(Establishment.class);
        for (Establishment card : Establishment.values()) {
            supply.put(card, card.supplyCount());
        }
        return supply;
    }

    static GameState newGame(int players) {
        return GameSetup.newGame("g", IntStream.rangeClosed(1, players)
                .mapToObj(i -> new PlayerInfo("p" + i, "P" + i))
                .toList());
    }
}
