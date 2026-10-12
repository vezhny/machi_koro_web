package com.machikoro.core.engine;

import static com.machikoro.core.Establishment.BUSINESS_CENTER;
import static com.machikoro.core.Establishment.MINE;
import static com.machikoro.core.Establishment.STADIUM;
import static com.machikoro.core.Establishment.TV_STATION;
import static com.machikoro.core.Establishment.WHEAT_FIELD;
import static com.machikoro.core.engine.Fixtures.apply;
import static com.machikoro.core.engine.Fixtures.assertRejected;
import static com.machikoro.core.engine.Fixtures.game;
import static com.machikoro.core.engine.Fixtures.inBuild;
import static com.machikoro.core.engine.Fixtures.newGame;
import static com.machikoro.core.engine.Fixtures.player;
import static com.machikoro.core.engine.Fixtures.roll;
import static org.assertj.core.api.Assertions.assertThat;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.command.Build;
import com.machikoro.core.command.BuildTarget;
import com.machikoro.core.command.ChooseTarget;
import com.machikoro.core.command.Command;
import com.machikoro.core.command.KeepRoll;
import com.machikoro.core.command.Pass;
import com.machikoro.core.command.Reroll;
import com.machikoro.core.command.RollDice;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InvalidCommandTest {

    @Nested
    class Turn {

        private final GameState start = newGame(2);

        @Test
        void notYourTurn() {
            assertRejected(start, new RollDice("p2", 0, 1), RuleViolation.NOT_YOUR_TURN);
        }

        @Test
        void unknownPlayer() {
            assertRejected(start, new RollDice("ghost", 0, 1), RuleViolation.UNKNOWN_PLAYER);
        }

        @Test
        void staleVersion() {
            GameState rolled = roll(start, 6).state();

            assertRejected(rolled, new Pass("p1", 0), RuleViolation.STALE_VERSION);
            assertRejected(rolled, new Pass("p1", 2), RuleViolation.STALE_VERSION);
        }

        @Test
        void turnCheckedBeforeVersion() {
            assertRejected(start, new RollDice("p2", 42, 1), RuleViolation.NOT_YOUR_TURN);
        }

        @Test
        void gameOverRejectsEverything() {
            GameState over = start.withWinner("p1");

            assertRejected(over, new RollDice("p1", 0, 1), RuleViolation.GAME_OVER);
            assertRejected(over, new Pass("p2", 0), RuleViolation.GAME_OVER);
        }
    }

    @Nested
    class WrongPhase {

        static Stream<Arguments> inRoll() {
            return Stream.of(
                    Arguments.of(new Build("p1", 0, BuildTarget.of(Establishment.RANCH))),
                    Arguments.of(new Pass("p1", 0)),
                    Arguments.of(new KeepRoll("p1", 0)),
                    Arguments.of(new Reroll("p1", 0, 1)),
                    Arguments.of(new ChooseTarget("p1", 0, "p2", null, null)));
        }

        @ParameterizedTest
        @MethodSource("inRoll")
        void rejectedInRollPhase(Command command) {
            assertRejected(newGame(2), command, RuleViolation.WRONG_PHASE);
        }

        static Stream<Arguments> inBuildPhase() {
            return Stream.of(
                    Arguments.of(new RollDice("p1", 0, 1)),
                    Arguments.of(new KeepRoll("p1", 0)),
                    Arguments.of(new Reroll("p1", 0, 1)),
                    Arguments.of(new ChooseTarget("p1", 0, "p2", null, null)));
        }

        @ParameterizedTest
        @MethodSource("inBuildPhase")
        void rejectedInBuildPhase(Command command) {
            GameState state = inBuild(player("p1", 10, Map.of()), player("p2", 0, Map.of()));

            assertRejected(state, command, RuleViolation.WRONG_PHASE);
        }

        @Test
        void cannotBuildOrPassWhileTargetPending() {
            GameState awaiting = roll(game(
                    player("p1", 10, Map.of(TV_STATION, 1)),
                    player("p2", 5, Map.of())), 6).state();

            assertRejected(awaiting, new Pass("p1", 1), RuleViolation.WRONG_PHASE);
            assertRejected(awaiting, new Build("p1", 1, BuildTarget.of(Establishment.RANCH)),
                    RuleViolation.WRONG_PHASE);
        }

        @Test
        void secondPurchaseInSameTurn() {
            GameState build = inBuild(player("p1", 10, Map.of()), player("p2", 0, Map.of()));
            GameState afterFirst = apply(build, new Build("p1", 0, BuildTarget.of(Establishment.RANCH))).state();

            assertRejected(afterFirst, new Build("p1", 1, BuildTarget.of(Establishment.RANCH)),
                    RuleViolation.NOT_YOUR_TURN);
        }

        @Test
        void secondPurchaseDuringExtraTurnNeedsNewRoll() {
            GameState start = game(
                    player("p1", 10, Map.of(), Landmark.TRAIN_STATION, Landmark.AMUSEMENT_PARK),
                    player("p2", 0, Map.of()));
            GameState build = roll(start, 1, 1).state();
            GameState extraTurn = apply(build, new Build("p1", 1, BuildTarget.of(Establishment.RANCH))).state();

            assertThat(extraTurn.activePlayer().id()).isEqualTo("p1");
            assertRejected(extraTurn, new Build("p1", 2, BuildTarget.of(Establishment.RANCH)),
                    RuleViolation.WRONG_PHASE);
        }
    }

    @Nested
    class Building {

        @Test
        void notEnoughCoinsForEstablishment() {
            GameState state = inBuild(player("p1", 5, Map.of()), player("p2", 0, Map.of()));

            assertRejected(state, new Build("p1", 0, BuildTarget.of(MINE)), RuleViolation.NOT_ENOUGH_COINS);
        }

        @Test
        void notEnoughCoinsForLandmark() {
            GameState state = inBuild(player("p1", 3, Map.of()), player("p2", 0, Map.of()));

            assertRejected(state, new Build("p1", 0, BuildTarget.of(Landmark.TRAIN_STATION)),
                    RuleViolation.NOT_ENOUGH_COINS);
        }

        @Test
        void exactCoinsAreEnough() {
            GameState state = inBuild(player("p1", 6, Map.of()), player("p2", 0, Map.of()));

            GameState after = apply(state, new Build("p1", 0, BuildTarget.of(MINE))).state();

            assertThat(after.player("p1").coins()).isZero();
            assertThat(after.player("p1").count(MINE)).isEqualTo(1);
        }

        @Test
        void duplicatePurpleCard() {
            GameState state = inBuild(player("p1", 20, Map.of(STADIUM, 1)), player("p2", 0, Map.of()));

            assertRejected(state, new Build("p1", 0, BuildTarget.of(STADIUM)), RuleViolation.PURPLE_ALREADY_OWNED);
        }

        @Test
        void differentPurpleCardsAllowed() {
            GameState state = inBuild(player("p1", 20, Map.of(STADIUM, 1)), player("p2", 0, Map.of()));

            GameState after = apply(state, new Build("p1", 0, BuildTarget.of(TV_STATION))).state();

            assertThat(after.player("p1").count(TV_STATION)).isEqualTo(1);
        }

        @Test
        void regularCardsMayRepeat() {
            GameState state = inBuild(player("p1", 20, Map.of(WHEAT_FIELD, 3)), player("p2", 0, Map.of()));

            GameState after = apply(state, new Build("p1", 0, BuildTarget.of(WHEAT_FIELD))).state();

            assertThat(after.player("p1").count(WHEAT_FIELD)).isEqualTo(4);
        }

        @Test
        void landmarkAlreadyBuilt() {
            GameState state = inBuild(player("p1", 20, Map.of(), Landmark.TRAIN_STATION), player("p2", 0, Map.of()));

            assertRejected(state, new Build("p1", 0, BuildTarget.of(Landmark.TRAIN_STATION)),
                    RuleViolation.LANDMARK_ALREADY_BUILT);
        }

        @Test
        void emptySupply() {
            Map<Establishment, Integer> supply = new EnumMap<>(Fixtures.fullSupply());
            supply.put(MINE, 0);
            GameState state = inBuild(player("p1", 20, Map.of()), player("p2", 0, Map.of())).withSupply(supply);

            assertRejected(state, new Build("p1", 0, BuildTarget.of(MINE)), RuleViolation.SUPPLY_EMPTY);
        }

        @Test
        void lastCardEmptiesSupply() {
            GameState state = inBuild(player("p1", 20, Map.of()), player("p2", 0, Map.of()))
                    .withSupply(Map.of(MINE, 1));

            GameState after = apply(state, new Build("p1", 0, BuildTarget.of(MINE))).state();

            assertThat(after.supplyOf(MINE)).isZero();
            assertThat(after.supply()).doesNotContainKey(MINE);
        }
    }

    @Nested
    class Targets {

        private final GameState tvAwaiting = roll(game(
                player("p1", 0, Map.of(TV_STATION, 1)),
                player("p2", 5, Map.of())), 6).state();

        private final GameState bcAwaiting = roll(game(
                player("p1", 0, Map.of(BUSINESS_CENTER, 1, WHEAT_FIELD, 1)),
                player("p2", 0, Map.of(MINE, 1, STADIUM, 1))), 6).state();

        @Test
        void tvStationNeedsTarget() {
            assertRejected(tvAwaiting, new ChooseTarget("p1", 1, null, null, null), RuleViolation.INVALID_TARGET);
        }

        @Test
        void cannotTargetYourself() {
            assertRejected(tvAwaiting, new ChooseTarget("p1", 1, "p1", null, null), RuleViolation.INVALID_TARGET);
            assertRejected(bcAwaiting, new ChooseTarget("p1", 1, "p1", WHEAT_FIELD, WHEAT_FIELD),
                    RuleViolation.INVALID_TARGET);
        }

        @Test
        void unknownTarget() {
            assertRejected(tvAwaiting, new ChooseTarget("p1", 1, "ghost", null, null), RuleViolation.INVALID_TARGET);
        }

        @Test
        void swapRequiresOwnedCards() {
            assertRejected(bcAwaiting, new ChooseTarget("p1", 1, "p2", Establishment.BAKERY, MINE),
                    RuleViolation.INVALID_SWAP);
            assertRejected(bcAwaiting, new ChooseTarget("p1", 1, "p2", WHEAT_FIELD, Establishment.FOREST),
                    RuleViolation.INVALID_SWAP);
            assertRejected(bcAwaiting, new ChooseTarget("p1", 1, "p2", null, MINE), RuleViolation.INVALID_SWAP);
            assertRejected(bcAwaiting, new ChooseTarget("p1", 1, "p2", WHEAT_FIELD, null),
                    RuleViolation.INVALID_SWAP);
        }

        @Test
        void purpleCardsCannotBeSwapped() {
            assertRejected(bcAwaiting, new ChooseTarget("p1", 1, "p2", BUSINESS_CENTER, MINE),
                    RuleViolation.INVALID_SWAP);
            assertRejected(bcAwaiting, new ChooseTarget("p1", 1, "p2", WHEAT_FIELD, STADIUM),
                    RuleViolation.INVALID_SWAP);
        }
    }
}
