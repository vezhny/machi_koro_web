package com.machikoro.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GameStateTest {

    private final GameState start = GameSetup.newGame("g1", GameSetupTest.players(2));

    @Test
    void withersReturnNewInstanceAndLeaveOriginalUntouched() {
        GameState changed = start
                .withPlayer(start.player("p2").withCoins(7))
                .withActivePlayerIndex(1)
                .withPhase(Phase.BUILD)
                .withTurn(TurnContext.start().withDice(List.of(3, 4)))
                .withSupply(Map.of(Establishment.MINE, 1))
                .nextVersion();

        assertThat(changed.player("p2").coins()).isEqualTo(7);
        assertThat(changed.activePlayer().id()).isEqualTo("p2");
        assertThat(changed.phase()).isEqualTo(Phase.BUILD);
        assertThat(changed.turn().diceSum()).isEqualTo(7);
        assertThat(changed.supply()).isEqualTo(Map.of(Establishment.MINE, 1));
        assertThat(changed.version()).isEqualTo(1);

        assertThat(start).isEqualTo(GameSetup.newGame("g1", GameSetupTest.players(2)));
    }

    @Test
    void withWinnerEndsTheGame() {
        GameState over = start.withWinner("p1");

        assertThat(over.phase()).isEqualTo(Phase.GAME_OVER);
        assertThat(over.winnerId()).isEqualTo("p1");
    }

    @Test
    void collectionsCannotBeModifiedFromOutside() {
        assertThatThrownBy(() -> start.players().add(start.activePlayer()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> start.supply().put(Establishment.MINE, 99))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructorCopiesInputCollections() {
        List<PlayerState> players = new ArrayList<>(start.players());
        Map<Establishment, Integer> supply = new EnumMap<>(start.supply());
        GameState state = new GameState("g1", 0, players, 0, Phase.ROLL, supply, TurnContext.start(), null);

        players.clear();
        supply.clear();

        assertThat(state.players()).hasSize(2);
        assertThat(state.supplyOf(Establishment.WHEAT_FIELD)).isEqualTo(6);
    }

    @Test
    void emptySupplyEntriesAreDropped() {
        GameState state = start.withSupply(Map.of(Establishment.MINE, 0, Establishment.CAFE, 2));

        assertThat(state.supply()).isEqualTo(Map.of(Establishment.CAFE, 2));
        assertThat(state.supplyOf(Establishment.MINE)).isZero();
    }

    @Test
    void looksUpPlayers() {
        assertThat(start.findPlayer("p2")).map(PlayerState::name).contains("Player 2");
        assertThat(start.findPlayer("nobody")).isEmpty();
        assertThatThrownBy(() -> start.player("nobody")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> start.withPlayer(new PlayerState("ghost", "G", 0, Map.of(), Set.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInconsistentState() {
        List<PlayerState> players = start.players();
        Map<Establishment, Integer> supply = start.supply();
        TurnContext turn = TurnContext.start();

        assertThatThrownBy(() -> new GameState("g", -1, players, 0, Phase.ROLL, supply, turn, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameState("g", 0, List.of(), 0, Phase.ROLL, supply, turn, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameState("g", 0, players, 2, Phase.ROLL, supply, turn, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameState("g", 0, List.of(players.get(0), players.get(0)), 0,
                Phase.ROLL, supply, turn, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameState("g", 0, players, 0, Phase.GAME_OVER, supply, turn, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameState("g", 0, players, 0, Phase.ROLL, supply, turn, "p1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
