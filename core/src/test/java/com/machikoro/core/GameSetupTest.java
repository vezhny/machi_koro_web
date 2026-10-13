package com.machikoro.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GameSetupTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void everyPlayerGetsStartingCoinsAndCards(int playerCount) {
        GameState state = GameSetup.newGame("g1", players(playerCount));

        assertThat(state.players()).hasSize(playerCount);
        assertThat(state.players()).allSatisfy(player -> {
            assertThat(player.coins()).isEqualTo(3);
            assertThat(player.establishments())
                    .isEqualTo(Map.of(Establishment.WHEAT_FIELD, 1, Establishment.BAKERY, 1));
            assertThat(player.landmarks()).isEmpty();
        });
    }

    @Test
    void keepsSeatingOrderAndNames() {
        GameState state = GameSetup.newGame("g1", players(3));

        assertThat(state.players()).extracting(PlayerState::id).containsExactly("p1", "p2", "p3");
        assertThat(state.players()).extracting(PlayerState::name).containsExactly("Player 1", "Player 2", "Player 3");
    }

    @Test
    void firstPlayerStartsInRollPhase() {
        GameState state = GameSetup.newGame("g1", players(2));

        assertThat(state.gameId()).isEqualTo("g1");
        assertThat(state.version()).isZero();
        assertThat(state.activePlayerIndex()).isZero();
        assertThat(state.activePlayer().id()).isEqualTo("p1");
        assertThat(state.phase()).isEqualTo(Phase.ROLL);
        assertThat(state.turn()).isEqualTo(TurnContext.start());
        assertThat(state.winnerId()).isNull();
    }

    @Test
    void bankHoldsSixRegularAndFourPurpleCardsOfEachKind() {
        GameState state = GameSetup.newGame("g1", players(4));

        for (Establishment card : Establishment.values()) {
            assertThat(state.supplyOf(card)).as(card.name()).isEqualTo(card.isPurple() ? 4 : 6);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5})
    void rejectsWrongPlayerCount(int playerCount) {
        assertThatThrownBy(() -> GameSetup.newGame("g1", players(playerCount)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicatePlayerIds() {
        List<PlayerInfo> players = List.of(new PlayerInfo("p1", "A"), new PlayerInfo("p1", "B"));

        assertThatThrownBy(() -> GameSetup.newGame("g1", players))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static List<PlayerInfo> players(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new PlayerInfo("p" + i, "Player " + i))
                .toList();
    }
}
