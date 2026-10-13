package com.machikoro.core.engine;

import static com.machikoro.core.Establishment.BAKERY;
import static com.machikoro.core.Establishment.BUSINESS_CENTER;
import static com.machikoro.core.Establishment.MINE;
import static com.machikoro.core.Establishment.STADIUM;
import static com.machikoro.core.Establishment.TV_STATION;
import static com.machikoro.core.Establishment.WHEAT_FIELD;
import static com.machikoro.core.engine.Fixtures.apply;
import static com.machikoro.core.engine.Fixtures.game;
import static com.machikoro.core.engine.Fixtures.player;
import static com.machikoro.core.engine.Fixtures.roll;
import static org.assertj.core.api.Assertions.assertThat;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameState;
import com.machikoro.core.Phase;
import com.machikoro.core.command.ChooseTarget;
import com.machikoro.core.event.CoinsTransferred;
import com.machikoro.core.event.EstablishmentsSwapped;
import com.machikoro.core.event.TargetRequired;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PurpleCardsTest {

    @Test
    void stadiumTakesTwoFromEachOpponentOrWhatTheyHave() {
        GameState start = game(
                player("p1", 0, Map.of(STADIUM, 1)),
                player("p2", 4, Map.of()),
                player("p3", 1, Map.of()),
                player("p4", 0, Map.of()));

        GameState state = roll(start, 6).state();

        assertThat(state.phase()).isEqualTo(Phase.BUILD);
        assertThat(state.player("p1").coins()).isEqualTo(3);
        assertThat(state.player("p2").coins()).isEqualTo(2);
        assertThat(state.player("p3").coins()).isZero();
        assertThat(state.player("p4").coins()).isZero();
    }

    @Test
    void opponentsStadiumDoesNothing() {
        GameState start = game(
                player("p1", 5, Map.of()),
                player("p2", 0, Map.of(STADIUM, 1)));

        GameState state = roll(start, 6).state();

        assertThat(state.player("p1").coins()).isEqualTo(5);
        assertThat(state.player("p2").coins()).isZero();
    }

    @Test
    void tvStationTakesWhatTargetHasWhenBelowFive() {
        GameState awaiting = roll(game(
                player("p1", 0, Map.of(TV_STATION, 1)),
                player("p2", 3, Map.of()),
                player("p3", 9, Map.of())), 6).state();

        GameState state = apply(awaiting, new ChooseTarget("p1", 1, "p2", null, null)).state();

        assertThat(state.player("p1").coins()).isEqualTo(3);
        assertThat(state.player("p2").coins()).isZero();
        assertThat(state.player("p3").coins()).isEqualTo(9);
    }

    @Test
    void tvStationOnBrokeTargetTransfersNothing() {
        GameState awaiting = roll(game(
                player("p1", 0, Map.of(TV_STATION, 1)),
                player("p2", 0, Map.of())), 6).state();

        GameResult result = apply(awaiting, new ChooseTarget("p1", 1, "p2", null, null));

        assertThat(result.state().phase()).isEqualTo(Phase.BUILD);
        assertThat(result.events()).isEmpty();
    }

    @Test
    void businessCenterSwapsNonPurpleCards() {
        GameState awaiting = roll(game(
                player("p1", 0, Map.of(BUSINESS_CENTER, 1, WHEAT_FIELD, 2)),
                player("p2", 0, Map.of(MINE, 1))), 6).state();
        assertThat(awaiting.phase()).isEqualTo(Phase.AWAITING_TARGET);

        GameResult result = apply(awaiting, new ChooseTarget("p1", 1, "p2", WHEAT_FIELD, MINE));

        GameState state = result.state();
        assertThat(state.phase()).isEqualTo(Phase.BUILD);
        assertThat(state.player("p1").establishments())
                .isEqualTo(Map.of(BUSINESS_CENTER, 1, WHEAT_FIELD, 1, MINE, 1));
        assertThat(state.player("p2").establishments()).isEqualTo(Map.of(WHEAT_FIELD, 1));
        assertThat(result.events()).containsExactly(new EstablishmentsSwapped("p1", "p2", WHEAT_FIELD, MINE));
    }

    @Test
    void businessCenterCanBeSkipped() {
        GameState awaiting = roll(game(
                player("p1", 0, Map.of(BUSINESS_CENTER, 1, BAKERY, 1)),
                player("p2", 0, Map.of(MINE, 1))), 6).state();

        GameResult result = apply(awaiting, new ChooseTarget("p1", 1, null, null, null));

        assertThat(result.state().phase()).isEqualTo(Phase.BUILD);
        assertThat(result.state().players()).isEqualTo(awaiting.players());
        assertThat(result.events()).isEmpty();
    }

    @Test
    void allPurpleCardsResolveInTableOrder() {
        GameState start = game(
                player("p1", 0, Map.of(STADIUM, 1, TV_STATION, 1, BUSINESS_CENTER, 1, BAKERY, 1)),
                player("p2", 10, Map.of(MINE, 1)));

        GameResult rolled = roll(start, 6);
        assertThat(rolled.state().player("p1").coins()).as("Stadium resolved immediately").isEqualTo(2);
        assertThat(rolled.state().turn().pendingTargets()).containsExactly(TV_STATION, BUSINESS_CENTER);
        assertThat(rolled.events()).endsWith(new TargetRequired("p1", TV_STATION));

        GameResult tv = apply(rolled.state(), new ChooseTarget("p1", 1, "p2", null, null));
        assertThat(tv.state().phase()).isEqualTo(Phase.AWAITING_TARGET);
        assertThat(tv.state().turn().pendingTargets()).containsExactly(BUSINESS_CENTER);
        assertThat(tv.events()).containsExactly(
                new CoinsTransferred("p2", "p1", TV_STATION, 5),
                new TargetRequired("p1", BUSINESS_CENTER));

        GameState done = apply(tv.state(), new ChooseTarget("p1", 2, "p2", BAKERY, MINE)).state();
        assertThat(done.phase()).isEqualTo(Phase.BUILD);
        assertThat(done.turn().pendingTargets()).isEmpty();
        assertThat(done.player("p1").coins()).isEqualTo(7);
        assertThat(done.player("p1").count(MINE)).isEqualTo(1);
        assertThat(done.player("p2").count(BAKERY)).isEqualTo(1);
    }

    @Test
    void ownedPurpleCardDoesNotActivateOnOtherNumbers() {
        GameState state = roll(game(
                player("p1", 0, Map.of(TV_STATION, 1, Establishment.WHEAT_FIELD, 1)),
                player("p2", 10, Map.of())), 1).state();

        assertThat(state.phase()).isEqualTo(Phase.BUILD);
    }
}
