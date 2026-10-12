package com.machikoro.core.engine;

import static com.machikoro.core.engine.Fixtures.game;
import static com.machikoro.core.engine.Fixtures.newGame;
import static com.machikoro.core.engine.Fixtures.player;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.Phase;
import com.machikoro.core.command.Build;
import com.machikoro.core.command.BuildTarget;
import com.machikoro.core.command.ChooseTarget;
import com.machikoro.core.command.KeepRoll;
import com.machikoro.core.command.Pass;
import com.machikoro.core.command.Reroll;
import com.machikoro.core.command.RollDice;
import com.machikoro.core.event.Built;
import com.machikoro.core.event.CoinsTransferred;
import com.machikoro.core.event.DiceRolled;
import com.machikoro.core.event.GameWon;
import com.machikoro.core.event.IncomeReceived;
import com.machikoro.core.event.TargetRequired;
import com.machikoro.core.event.TurnPassed;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Main turn flow. Card-by-card and violation coverage lives in phase 3 tests.
 */
class GameEngineTest {

    private final GameEngine engine = new GameEngine();

    @Test
    void rollResolvesIncomeAndMovesToBuild() {
        GameState start = newGame(2);

        GameResult result = engine.apply(start, new RollDice("p1", 0, 1), new FixedRandomSource(1));

        assertThat(result.state().version()).isEqualTo(1);
        assertThat(result.state().phase()).isEqualTo(Phase.BUILD);
        assertThat(result.state().turn().dice()).containsExactly(1);
        assertThat(result.state().player("p1").coins()).isEqualTo(4);
        assertThat(result.state().player("p2").coins()).isEqualTo(4);
        assertThat(result.events()).containsExactly(
                new DiceRolled("p1", List.of(1), false),
                new IncomeReceived("p1", Establishment.WHEAT_FIELD, 1),
                new IncomeReceived("p2", Establishment.WHEAT_FIELD, 1));
    }

    @Test
    void buildPaysTakesFromBankAndEndsTurn() {
        GameState inBuild = engine.apply(newGame(2), new RollDice("p1", 0, 1), new FixedRandomSource(6)).state();

        GameResult result = engine.apply(inBuild, new Build("p1", 1, BuildTarget.of(Establishment.RANCH)),
                new FixedRandomSource());

        GameState state = result.state();
        assertThat(state.player("p1").coins()).isEqualTo(2);
        assertThat(state.player("p1").count(Establishment.RANCH)).isEqualTo(1);
        assertThat(state.supplyOf(Establishment.RANCH)).isEqualTo(5);
        assertThat(state.activePlayer().id()).isEqualTo("p2");
        assertThat(state.phase()).isEqualTo(Phase.ROLL);
        assertThat(state.turn().rolled()).isFalse();
        assertThat(state.version()).isEqualTo(2);
        assertThat(result.events()).containsExactly(
                new Built("p1", BuildTarget.of(Establishment.RANCH)),
                new TurnPassed("p1", "p2"));
    }

    @Test
    void passEndsTurnAndWrapsAround() {
        GameState state = newGame(2);
        state = engine.apply(state, new RollDice("p1", 0, 1), new FixedRandomSource(6)).state();
        state = engine.apply(state, new Pass("p1", 1), new FixedRandomSource()).state();
        state = engine.apply(state, new RollDice("p2", 2, 1), new FixedRandomSource(6)).state();
        state = engine.apply(state, new Pass("p2", 3), new FixedRandomSource()).state();

        assertThat(state.activePlayer().id()).isEqualTo("p1");
        assertThat(state.version()).isEqualTo(4);
    }

    @Test
    void radioTowerOffersRerollOnce() {
        GameState start = game(
                player("p1", 0, Map.of(Establishment.WHEAT_FIELD, 1), Landmark.RADIO_TOWER),
                player("p2", 0, Map.of()));

        GameState decision = engine.apply(start, new RollDice("p1", 0, 1), new FixedRandomSource(4)).state();
        assertThat(decision.phase()).isEqualTo(Phase.REROLL_DECISION);
        assertThat(decision.player("p1").coins()).isZero();

        GameResult rerolled = engine.apply(decision, new Reroll("p1", 1, 1), new FixedRandomSource(1));
        assertThat(rerolled.state().phase()).isEqualTo(Phase.BUILD);
        assertThat(rerolled.state().turn().rerollUsed()).isTrue();
        assertThat(rerolled.state().player("p1").coins()).isEqualTo(1);
        assertThat(rerolled.events().getFirst()).isEqualTo(new DiceRolled("p1", List.of(1), true));
    }

    @Test
    void keepRollResolvesCurrentDice() {
        GameState start = game(
                player("p1", 0, Map.of(Establishment.WHEAT_FIELD, 1), Landmark.RADIO_TOWER),
                player("p2", 0, Map.of()));
        GameState decision = engine.apply(start, new RollDice("p1", 0, 1), new FixedRandomSource(1)).state();

        GameState kept = engine.apply(decision, new KeepRoll("p1", 1), new FixedRandomSource()).state();

        assertThat(kept.phase()).isEqualTo(Phase.BUILD);
        assertThat(kept.player("p1").coins()).isEqualTo(1);
    }

    @Test
    void tvStationWaitsForTargetThenTakesCoins() {
        GameState start = game(
                player("p1", 0, Map.of(Establishment.TV_STATION, 1)),
                player("p2", 7, Map.of()));

        GameResult rolled = engine.apply(start, new RollDice("p1", 0, 1), new FixedRandomSource(6));
        assertThat(rolled.state().phase()).isEqualTo(Phase.AWAITING_TARGET);
        assertThat(rolled.events()).contains(new TargetRequired("p1", Establishment.TV_STATION));

        GameResult chosen = engine.apply(rolled.state(), new ChooseTarget("p1", 1, "p2", null, null),
                new FixedRandomSource());
        assertThat(chosen.state().phase()).isEqualTo(Phase.BUILD);
        assertThat(chosen.state().player("p1").coins()).isEqualTo(5);
        assertThat(chosen.state().player("p2").coins()).isEqualTo(2);
        assertThat(chosen.events()).containsExactly(
                new CoinsTransferred("p2", "p1", Establishment.TV_STATION, 5));
    }

    @Test
    void fourthLandmarkWinsTheGame() {
        GameState start = game(
                player("p1", 22, Map.of(), Landmark.TRAIN_STATION, Landmark.SHOPPING_MALL, Landmark.AMUSEMENT_PARK),
                player("p2", 0, Map.of()));
        GameState inBuild = engine.apply(start, new RollDice("p1", 0, 1), new FixedRandomSource(5)).state();

        GameResult result = engine.apply(inBuild, new Build("p1", 1, BuildTarget.of(Landmark.RADIO_TOWER)),
                new FixedRandomSource());

        assertThat(result.state().phase()).isEqualTo(Phase.GAME_OVER);
        assertThat(result.state().winnerId()).isEqualTo("p1");
        assertThat(result.state().player("p1").landmarks()).isEqualTo(EnumSet.allOf(Landmark.class));
        assertThat(result.events()).containsExactly(
                new Built("p1", BuildTarget.of(Landmark.RADIO_TOWER)),
                new GameWon("p1"));
    }

    @Test
    void rejectedCommandLeavesStateUntouched() {
        GameState start = newGame(2);

        assertThatThrownBy(() -> engine.apply(start, new RollDice("p2", 0, 1), new FixedRandomSource(1)))
                .isInstanceOfSatisfying(IllegalMoveException.class,
                        e -> assertThat(e.code()).isEqualTo(RuleViolation.NOT_YOUR_TURN));
        assertThat(start).isEqualTo(newGame(2));
    }
}
