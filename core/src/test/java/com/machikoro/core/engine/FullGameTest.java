package com.machikoro.core.engine;

import static com.machikoro.core.engine.Fixtures.newGame;
import static org.assertj.core.api.Assertions.assertThat;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.Phase;
import com.machikoro.core.PlayerState;
import com.machikoro.core.command.Build;
import com.machikoro.core.command.BuildTarget;
import com.machikoro.core.command.ChooseTarget;
import com.machikoro.core.command.Command;
import com.machikoro.core.command.KeepRoll;
import com.machikoro.core.command.Pass;
import com.machikoro.core.command.RollDice;
import com.machikoro.core.event.GameEvent;
import com.machikoro.core.event.GameWon;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Plays whole games with a fixed dice sequence and a simple scripted strategy until someone wins.
 */
class FullGameTest {

    private static final int MAX_COMMANDS = 5_000;
    private static final List<Establishment> SHOPPING_LIST = List.of(
            Establishment.CONVENIENCE_STORE, Establishment.RANCH, Establishment.BAKERY, Establishment.FOREST,
            Establishment.WHEAT_FIELD, Establishment.CAFE, Establishment.STADIUM, Establishment.TV_STATION,
            Establishment.BUSINESS_CENTER, Establishment.CHEESE_FACTORY, Establishment.MINE);
    private static final int CITY_SIZE_LIMIT = 12;

    private final GameEngine engine = new GameEngine();

    record Playthrough(GameState finalState, List<GameEvent> events, int commands) {
    }

    @ParameterizedTest(name = "{0} players")
    @ValueSource(ints = {2, 3, 4})
    void gameEndsWithWinnerOwningAllLandmarks(int players) {
        Playthrough game = play(newGame(players), 42L + players);

        GameState end = game.finalState();
        assertThat(end.phase()).isEqualTo(Phase.GAME_OVER);
        PlayerState winner = end.player(end.winnerId());
        assertThat(winner.hasAllLandmarks()).isTrue();
        assertThat(end.players()).filteredOn(p -> !p.id().equals(winner.id()))
                .noneMatch(PlayerState::hasAllLandmarks);
        assertThat(end.version()).isEqualTo(game.commands());
        assertThat(game.events()).last().isEqualTo(new GameWon(winner.id()));
        assertThat(game.events()).filteredOn(GameWon.class::isInstance).hasSize(1);
    }

    @ParameterizedTest(name = "{0} players")
    @ValueSource(ints = {2, 3, 4})
    void establishmentsAreConserved(int players) {
        GameState end = play(newGame(players), 7L * players).finalState();

        for (Establishment card : Establishment.values()) {
            int owned = end.players().stream().mapToInt(p -> p.count(card)).sum();
            int startingCards = card == Establishment.WHEAT_FIELD || card == Establishment.BAKERY ? players : 0;
            assertThat(end.supplyOf(card) + owned).as(card.name()).isEqualTo(card.supplyCount() + startingCards);
        }
    }

    @Test
    void sameDiceSameGame() {
        Playthrough first = play(newGame(3), 2024L);
        Playthrough second = play(newGame(3), 2024L);

        assertThat(second.finalState()).isEqualTo(first.finalState());
        assertThat(second.events()).isEqualTo(first.events());
    }

    @Test
    void gameOverStopsFurtherCommands() {
        GameState end = play(newGame(2), 1L).finalState();

        Fixtures.assertRejected(end, new Pass(end.activePlayer().id(), end.version()), RuleViolation.GAME_OVER);
    }

    private Playthrough play(GameState start, long seed) {
        Random seeded = new Random(seed);
        FixedRandomSource dice = new FixedRandomSource(
                IntStream.generate(() -> seeded.nextInt(6) + 1).limit(4 * MAX_COMMANDS).toArray());

        GameState state = start;
        List<GameEvent> events = new ArrayList<>();
        int commands = 0;
        while (state.phase() != Phase.GAME_OVER) {
            assertThat(commands).as("game should finish").isLessThan(MAX_COMMANDS);
            GameResult result = engine.apply(state, decide(state), dice);
            assertThat(result.state().version()).isEqualTo(state.version() + 1);
            state = result.state();
            events.addAll(result.events());
            commands++;
        }
        return new Playthrough(state, events, commands);
    }

    /** Scripted strategy: rich player saves for landmarks in order, otherwise grows the city. */
    private static Command decide(GameState state) {
        PlayerState me = state.activePlayer();
        long version = state.version();
        return switch (state.phase()) {
            case ROLL -> new RollDice(me.id(), version, me.has(Landmark.TRAIN_STATION) && me.count(Establishment.MINE) > 0 ? 2 : 1);
            case REROLL_DECISION -> new KeepRoll(me.id(), version);
            case AWAITING_TARGET -> chooseTarget(state, me);
            case BUILD -> chooseBuild(state, me);
            case GAME_OVER -> throw new IllegalStateException("Game is over");
        };
    }

    private static Command chooseTarget(GameState state, PlayerState me) {
        PlayerState richest = state.players().stream()
                .filter(p -> !p.id().equals(me.id()))
                .max((a, b) -> Integer.compare(a.coins(), b.coins()))
                .orElseThrow();
        Establishment pending = state.turn().pendingTargets().getFirst();
        if (pending == Establishment.TV_STATION) {
            return new ChooseTarget(me.id(), state.version(), richest.id(), null, null);
        }
        if (me.count(Establishment.WHEAT_FIELD) > 0 && richest.count(Establishment.MINE) > 0) {
            return new ChooseTarget(me.id(), state.version(), richest.id(),
                    Establishment.WHEAT_FIELD, Establishment.MINE);
        }
        return new ChooseTarget(me.id(), state.version(), null, null, null);
    }

    private static Command chooseBuild(GameState state, PlayerState me) {
        for (Landmark landmark : Landmark.values()) {
            if (!me.has(landmark)) {
                if (me.coins() >= landmark.cost()) {
                    return new Build(me.id(), state.version(), BuildTarget.of(landmark));
                }
                break;
            }
        }
        int citySize = me.establishments().values().stream().mapToInt(Integer::intValue).sum();
        if (citySize < CITY_SIZE_LIMIT) {
            for (Establishment card : SHOPPING_LIST) {
                boolean allowed = state.supplyOf(card) > 0 && me.coins() >= card.cost()
                        && !(card.isPurple() && me.count(card) > 0);
                if (allowed) {
                    return new Build(me.id(), state.version(), BuildTarget.of(card));
                }
            }
        }
        return new Pass(me.id(), state.version());
    }
}
