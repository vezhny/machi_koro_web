package com.machikoro.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameSetup;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.Phase;
import com.machikoro.core.PlayerInfo;
import com.machikoro.core.PlayerState;
import com.machikoro.core.TurnContext;
import com.machikoro.core.command.Command;
import com.machikoro.core.command.RollDice;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Builders for hand-crafted game states and shortcuts for applying commands.
 */
final class Fixtures {

    static final GameEngine ENGINE = new GameEngine();

    private Fixtures() {
    }

    static PlayerState player(String id, int coins, Map<Establishment, Integer> cards, Landmark... landmarks) {
        return new PlayerState(id, id.toUpperCase(), coins, cards, Set.of(landmarks));
    }

    /** First player is active, phase ROLL, full bank. */
    static GameState game(PlayerState... players) {
        return new GameState("g", 0, List.of(players), 0, Phase.ROLL, fullSupply(), TurnContext.start(), null);
    }

    /** Same as {@link #game} but already in BUILD after rolling {@code 1}. */
    static GameState inBuild(PlayerState... players) {
        return game(players).withPhase(Phase.BUILD).withTurn(TurnContext.start().withDice(List.of(1)));
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

    /** Active player rolls {@code dice.length} dice showing {@code dice}. */
    static GameResult roll(GameState state, int... dice) {
        return ENGINE.apply(state, new RollDice(state.activePlayer().id(), state.version(), dice.length),
                new FixedRandomSource(dice));
    }

    static GameResult apply(GameState state, Command command, int... dice) {
        return ENGINE.apply(state, command, new FixedRandomSource(dice));
    }

    static void assertRejected(GameState state, Command command, RuleViolation expected) {
        assertThatThrownBy(() -> ENGINE.apply(state, command, new FixedRandomSource(1, 1, 1, 1)))
                .isInstanceOfSatisfying(IllegalMoveException.class, e -> {
                    assertThat(e.code()).isEqualTo(expected);
                    assertThat(e.getMessage()).isNotBlank();
                });
    }
}
