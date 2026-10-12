package com.machikoro.core.engine;

import static com.machikoro.core.Establishment.MINE;
import static com.machikoro.core.Establishment.WHEAT_FIELD;
import static com.machikoro.core.engine.Fixtures.apply;
import static com.machikoro.core.engine.Fixtures.assertRejected;
import static com.machikoro.core.engine.Fixtures.game;
import static com.machikoro.core.engine.Fixtures.player;
import static com.machikoro.core.engine.Fixtures.roll;
import static org.assertj.core.api.Assertions.assertThat;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.Phase;
import com.machikoro.core.command.Build;
import com.machikoro.core.command.BuildTarget;
import com.machikoro.core.command.KeepRoll;
import com.machikoro.core.command.Pass;
import com.machikoro.core.command.Reroll;
import com.machikoro.core.command.RollDice;
import com.machikoro.core.event.ExtraTurnGranted;
import com.machikoro.core.event.TurnPassed;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiceRulesTest {

    @Nested
    class TrainStation {

        @Test
        void twoDiceRejectedWithoutTrainStation() {
            GameState start = game(player("p1", 0, Map.of()), player("p2", 0, Map.of()));

            assertRejected(start, new RollDice("p1", 0, 2), RuleViolation.TRAIN_STATION_REQUIRED);
        }

        @Test
        void twoDiceSumWithTrainStation() {
            GameState start = game(
                    player("p1", 0, Map.of(MINE, 1), Landmark.TRAIN_STATION),
                    player("p2", 0, Map.of()));

            GameState state = roll(start, 3, 6).state();

            assertThat(state.turn().dice()).containsExactly(3, 6);
            assertThat(state.turn().diceSum()).isEqualTo(9);
            assertThat(state.player("p1").coins()).isEqualTo(5);
        }

        @Test
        void oneDieStillAllowedWithTrainStation() {
            GameState start = game(
                    player("p1", 0, Map.of(WHEAT_FIELD, 1), Landmark.TRAIN_STATION),
                    player("p2", 0, Map.of()));

            assertThat(roll(start, 1).state().player("p1").coins()).isEqualTo(1);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 3, -1})
        void invalidDiceCount(int diceCount) {
            GameState start = game(player("p1", 0, Map.of(), Landmark.TRAIN_STATION), player("p2", 0, Map.of()));

            assertRejected(start, new RollDice("p1", 0, diceCount), RuleViolation.INVALID_DICE_COUNT);
        }
    }

    @Nested
    class RadioTower {

        private final GameState start = game(
                player("p1", 0, Map.of(WHEAT_FIELD, 1, MINE, 1), Landmark.RADIO_TOWER, Landmark.TRAIN_STATION),
                player("p2", 0, Map.of()));

        @Test
        void noIncomeUntilDecision() {
            GameState decision = roll(start, 1).state();

            assertThat(decision.phase()).isEqualTo(Phase.REROLL_DECISION);
            assertThat(decision.player("p1").coins()).isZero();
            assertThat(decision.turn().rerollUsed()).isFalse();
        }

        @Test
        void rerollReplacesResultAndMayChangeDiceCount() {
            GameState decision = roll(start, 1).state();

            GameState state = apply(decision, new Reroll("p1", 1, 2), 4, 5).state();

            assertThat(state.turn().dice()).containsExactly(4, 5);
            assertThat(state.player("p1").coins()).as("Mine paid, Wheat Field not").isEqualTo(5);
        }

        @Test
        void rerollAllowedOnlyOncePerTurn() {
            GameState decision = roll(start, 1).state();
            GameState rerolled = apply(decision, new Reroll("p1", 1, 1), 6).state();

            assertThat(rerolled.phase()).isEqualTo(Phase.BUILD);
            assertRejected(rerolled, new Reroll("p1", 2, 1), RuleViolation.WRONG_PHASE);
        }

        @Test
        void oldResultCannotBeRestoredAfterReroll() {
            GameState decision = roll(start, 1).state();
            GameState rerolled = apply(decision, new Reroll("p1", 1, 1), 6).state();

            assertThat(rerolled.player("p1").coins()).as("Wheat Field from the discarded 1").isZero();
            assertRejected(rerolled, new KeepRoll("p1", 2), RuleViolation.WRONG_PHASE);
        }

        @Test
        void rerollWithTwoDiceNeedsTrainStation() {
            GameState noTrain = game(
                    player("p1", 0, Map.of(), Landmark.RADIO_TOWER),
                    player("p2", 0, Map.of()));
            GameState decision = roll(noTrain, 2).state();

            assertRejected(decision, new Reroll("p1", 1, 2), RuleViolation.TRAIN_STATION_REQUIRED);
        }

        @Test
        void rerollAvailableAgainNextTurn() {
            GameState decision = roll(start, 6).state();
            GameState build = apply(decision, new Reroll("p1", 1, 1), 6).state();
            GameState p2Turn = apply(build, new Pass("p1", 2)).state();
            GameState p1Turn = apply(roll(p2Turn, 6).state(), new Pass("p2", 4)).state();

            assertThat(roll(p1Turn, 6).state().phase()).isEqualTo(Phase.REROLL_DECISION);
        }

        @Test
        void withoutRadioTowerIncomeResolvesImmediately() {
            GameState plain = game(player("p1", 0, Map.of(WHEAT_FIELD, 1)), player("p2", 0, Map.of()));

            GameState state = roll(plain, 1).state();

            assertThat(state.phase()).isEqualTo(Phase.BUILD);
            assertRejected(state, new Reroll("p1", 1, 1), RuleViolation.WRONG_PHASE);
        }
    }

    @Nested
    class AmusementPark {

        @Test
        void doublesGrantExtraTurnAfterPass() {
            GameState start = game(
                    player("p1", 0, Map.of(), Landmark.TRAIN_STATION, Landmark.AMUSEMENT_PARK),
                    player("p2", 0, Map.of()));
            GameState build = roll(start, 3, 3).state();

            GameResult result = apply(build, new Pass("p1", 1));

            assertThat(result.state().activePlayer().id()).isEqualTo("p1");
            assertThat(result.state().phase()).isEqualTo(Phase.ROLL);
            assertThat(result.state().turn().rolled()).isFalse();
            assertThat(result.events()).containsExactly(new ExtraTurnGranted("p1"));
        }

        @Test
        void doublesGrantExtraTurnAfterBuild() {
            GameState start = game(
                    player("p1", 5, Map.of(), Landmark.TRAIN_STATION, Landmark.AMUSEMENT_PARK),
                    player("p2", 0, Map.of()));
            GameState build = roll(start, 2, 2).state();

            GameResult result = apply(build, new Build("p1", 1, BuildTarget.of(Establishment.RANCH)));

            assertThat(result.state().activePlayer().id()).isEqualTo("p1");
            assertThat(result.events()).endsWith(new ExtraTurnGranted("p1"));
        }

        @Test
        void noExtraTurnWithoutDoubles() {
            GameState start = game(
                    player("p1", 0, Map.of(), Landmark.TRAIN_STATION, Landmark.AMUSEMENT_PARK),
                    player("p2", 0, Map.of()));
            GameState build = roll(start, 3, 4).state();

            GameResult result = apply(build, new Pass("p1", 1));

            assertThat(result.state().activePlayer().id()).isEqualTo("p2");
            assertThat(result.events()).containsExactly(new TurnPassed("p1", "p2"));
        }

        @Test
        void noExtraTurnWithoutPark() {
            GameState start = game(
                    player("p1", 0, Map.of(), Landmark.TRAIN_STATION),
                    player("p2", 0, Map.of()));
            GameState build = roll(start, 5, 5).state();

            assertThat(apply(build, new Pass("p1", 1)).state().activePlayer().id()).isEqualTo("p2");
        }

        @Test
        void singleDieIsNeverDoubles() {
            GameState start = game(
                    player("p1", 0, Map.of(), Landmark.AMUSEMENT_PARK),
                    player("p2", 0, Map.of()));
            GameState build = roll(start, 4).state();

            assertThat(apply(build, new Pass("p1", 1)).state().activePlayer().id()).isEqualTo("p2");
        }

        @Test
        void doublesFromRerollCount() {
            GameState start = game(
                    player("p1", 0, Map.of(), Landmark.TRAIN_STATION, Landmark.AMUSEMENT_PARK,
                            Landmark.RADIO_TOWER),
                    player("p2", 0, Map.of()));
            GameState decision = roll(start, 1, 2).state();
            GameState build = apply(decision, new Reroll("p1", 1, 2), 4, 4).state();

            assertThat(build.turn().dice()).isEqualTo(List.of(4, 4));
            assertThat(apply(build, new Pass("p1", 2)).state().activePlayer().id()).isEqualTo("p1");
        }
    }
}
