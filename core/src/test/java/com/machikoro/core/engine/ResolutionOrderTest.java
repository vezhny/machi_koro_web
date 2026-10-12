package com.machikoro.core.engine;

import static com.machikoro.core.Establishment.BAKERY;
import static com.machikoro.core.Establishment.CAFE;
import static com.machikoro.core.Establishment.FAMILY_RESTAURANT;
import static com.machikoro.core.Establishment.MINE;
import static com.machikoro.core.Establishment.RANCH;
import static com.machikoro.core.Establishment.STADIUM;
import static com.machikoro.core.engine.Fixtures.game;
import static com.machikoro.core.engine.Fixtures.player;
import static com.machikoro.core.engine.Fixtures.roll;
import static org.assertj.core.api.Assertions.assertThat;

import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.event.CoinsTransferred;
import com.machikoro.core.event.DiceRolled;
import com.machikoro.core.event.IncomeReceived;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResolutionOrderTest {

    @Test
    void redResolvesBeforeGreen() {
        // Broke active player: Cafe gets nothing because Bakery income arrives only afterwards.
        GameState start = game(
                player("p1", 0, Map.of(BAKERY, 1)),
                player("p2", 0, Map.of(CAFE, 1)));

        GameResult result = roll(start, 3);

        assertThat(result.state().player("p1").coins()).isEqualTo(1);
        assertThat(result.state().player("p2").coins()).isZero();
        assertThat(result.events()).containsExactly(
                new DiceRolled("p1", List.of(3), false),
                new IncomeReceived("p1", BAKERY, 1));
    }

    @Test
    void redResolvesBeforeBlue() {
        GameState start = game(
                player("p1", 0, Map.of(MINE, 1), Landmark.TRAIN_STATION),
                player("p2", 0, Map.of(FAMILY_RESTAURANT, 1)));

        GameResult result = roll(start, 4, 5);

        assertThat(result.state().player("p1").coins()).isEqualTo(5);
        assertThat(result.state().player("p2").coins()).isZero();
    }

    @Test
    void blueForEveryoneResolvesBeforeGreen() {
        GameState start = game(
                player("p1", 3, Map.of(BAKERY, 1, RANCH, 1)),
                player("p2", 0, Map.of(RANCH, 1)));

        GameResult result = roll(start, 2);

        assertThat(result.events()).containsExactly(
                new DiceRolled("p1", List.of(2), false),
                new IncomeReceived("p1", RANCH, 1),
                new IncomeReceived("p2", RANCH, 1),
                new IncomeReceived("p1", BAKERY, 1));
    }

    @Test
    void redCardsPaidCounterclockwiseWhileMoneyLasts() {
        // p1 active (index 0): payment order is p4 (index 3), then p3, then p2.
        GameState start = game(
                player("p1", 3, Map.of()),
                player("p2", 0, Map.of(CAFE, 1)),
                player("p3", 0, Map.of(CAFE, 1)),
                player("p4", 0, Map.of(CAFE, 2)));

        GameResult result = roll(start, 3);

        GameState state = result.state();
        assertThat(state.player("p1").coins()).isZero();
        assertThat(state.player("p4").coins()).isEqualTo(2);
        assertThat(state.player("p3").coins()).isEqualTo(1);
        assertThat(state.player("p2").coins()).isZero();
        assertThat(result.events()).containsExactly(
                new DiceRolled("p1", List.of(3), false),
                new CoinsTransferred("p1", "p4", CAFE, 2),
                new CoinsTransferred("p1", "p3", CAFE, 1));
    }

    @Test
    void counterclockwiseOrderWrapsFromMiddleSeat() {
        // p3 active (index 2): order is p2, p1, p4. Only 1 coin: p2 gets it.
        GameState start = game(
                player("p1", 0, Map.of(CAFE, 1)),
                player("p2", 0, Map.of(CAFE, 1)),
                player("p3", 1, Map.of()),
                player("p4", 0, Map.of(CAFE, 1)))
                .withActivePlayerIndex(2);

        GameState state = roll(start, 3).state();

        assertThat(state.player("p2").coins()).isEqualTo(1);
        assertThat(state.player("p1").coins()).isZero();
        assertThat(state.player("p4").coins()).isZero();
    }

    @Test
    void partialPaymentWithinOneOwnersCards() {
        GameState start = game(
                player("p1", 3, Map.of(), Landmark.TRAIN_STATION),
                player("p2", 0, Map.of(FAMILY_RESTAURANT, 2)));

        GameResult result = roll(start, 4, 5);

        assertThat(result.state().player("p1").coins()).isZero();
        assertThat(result.state().player("p2").coins()).isEqualTo(3);
        assertThat(result.events()).contains(new CoinsTransferred("p1", "p2", FAMILY_RESTAURANT, 3));
    }

    /**
     * In the base game no blue or green card activates on 6, so "purple after blue/green" is not
     * observable through income; it is covered by the resolver order and the event sequence here.
     */
    @Test
    void stadiumTakesFromOpponentsClockwise() {
        GameState start = game(
                player("p1", 0, Map.of(STADIUM, 1)),
                player("p2", 5, Map.of()),
                player("p3", 1, Map.of()));

        GameResult result = roll(start, 6);

        assertThat(result.state().player("p1").coins()).isEqualTo(3);
        assertThat(result.state().player("p2").coins()).isEqualTo(3);
        assertThat(result.state().player("p3").coins()).isZero();
        assertThat(result.events()).containsExactly(
                new DiceRolled("p1", List.of(6), false),
                new CoinsTransferred("p2", "p1", STADIUM, 2),
                new CoinsTransferred("p3", "p1", STADIUM, 1));
    }
}
