package com.machikoro.core.engine;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.Phase;
import com.machikoro.core.PlayerState;
import com.machikoro.core.TurnContext;
import com.machikoro.core.command.Build;
import com.machikoro.core.command.BuildTarget;
import com.machikoro.core.command.ChooseTarget;
import com.machikoro.core.command.Command;
import com.machikoro.core.command.KeepRoll;
import com.machikoro.core.command.Pass;
import com.machikoro.core.command.Reroll;
import com.machikoro.core.command.RollDice;
import com.machikoro.core.event.Built;
import com.machikoro.core.event.CoinsTransferred;
import com.machikoro.core.event.DiceRolled;
import com.machikoro.core.event.EstablishmentsSwapped;
import com.machikoro.core.event.ExtraTurnGranted;
import com.machikoro.core.event.GameEvent;
import com.machikoro.core.event.GameWon;
import com.machikoro.core.event.TargetRequired;
import com.machikoro.core.event.TurnPassed;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Validates a command against the rules and applies it. Stateless and thread-safe.
 */
public final class GameEngine {

    private final IncomeResolver incomeResolver = new IncomeResolver();

    /**
     * @return the new state (version incremented) and the events that happened
     * @throws IllegalMoveException if the command breaks the rules; nothing is changed then
     */
    public GameResult apply(GameState state, Command command, RandomSource rng) {
        validateCommon(state, command);
        List<GameEvent> events = new ArrayList<>();
        GameState next = switch (command) {
            case RollDice roll -> {
                requirePhase(state, Phase.ROLL);
                yield roll(state, roll.diceCount(), false, rng, events);
            }
            case Reroll reroll -> {
                requirePhase(state, Phase.REROLL_DECISION);
                yield roll(state, reroll.diceCount(), true, rng, events);
            }
            case KeepRoll keep -> {
                requirePhase(state, Phase.REROLL_DECISION);
                yield resolveIncome(state, events);
            }
            case ChooseTarget choice -> {
                requirePhase(state, Phase.AWAITING_TARGET);
                yield chooseTarget(state, choice, events);
            }
            case Build build -> {
                requirePhase(state, Phase.BUILD);
                yield build(state, build.target(), events);
            }
            case Pass pass -> {
                requirePhase(state, Phase.BUILD);
                yield endTurn(state, events);
            }
        };
        return new GameResult(next.nextVersion(), events);
    }

    private static void validateCommon(GameState state, Command command) {
        if (state.phase() == Phase.GAME_OVER) {
            throw new IllegalMoveException(RuleViolation.GAME_OVER, "The game is over");
        }
        if (state.findPlayer(command.playerId()).isEmpty()) {
            throw new IllegalMoveException(RuleViolation.UNKNOWN_PLAYER, "Unknown player: " + command.playerId());
        }
        if (!state.activePlayer().id().equals(command.playerId())) {
            throw new IllegalMoveException(RuleViolation.NOT_YOUR_TURN,
                    "It is " + state.activePlayer().id() + "'s turn");
        }
        if (command.expectedVersion() != state.version()) {
            throw new IllegalMoveException(RuleViolation.STALE_VERSION,
                    "Expected version " + state.version() + ", got " + command.expectedVersion());
        }
    }

    private static void requirePhase(GameState state, Phase expected) {
        if (state.phase() != expected) {
            throw new IllegalMoveException(RuleViolation.WRONG_PHASE,
                    "Command needs phase " + expected + ", current phase is " + state.phase());
        }
    }

    private GameState roll(GameState state, int diceCount, boolean reroll, RandomSource rng,
                           List<GameEvent> events) {
        PlayerState player = state.activePlayer();
        validateDiceCount(player, diceCount);

        List<Integer> dice = new ArrayList<>(diceCount);
        for (int i = 0; i < diceCount; i++) {
            dice.add(rng.rollDie());
        }
        TurnContext turn = state.turn().withDice(dice);
        if (reroll) {
            turn = turn.withRerollUsed();
        }
        events.add(new DiceRolled(player.id(), dice, reroll));
        GameState rolled = state.withTurn(turn);

        if (player.has(Landmark.RADIO_TOWER) && !turn.rerollUsed()) {
            return rolled.withPhase(Phase.REROLL_DECISION);
        }
        return resolveIncome(rolled, events);
    }

    private static void validateDiceCount(PlayerState player, int diceCount) {
        if (diceCount != 1 && diceCount != 2) {
            throw new IllegalMoveException(RuleViolation.INVALID_DICE_COUNT,
                    "Dice count must be 1 or 2, got " + diceCount);
        }
        if (diceCount == 2 && !player.has(Landmark.TRAIN_STATION)) {
            throw new IllegalMoveException(RuleViolation.TRAIN_STATION_REQUIRED,
                    "Rolling two dice requires Train Station");
        }
    }

    private GameState resolveIncome(GameState state, List<GameEvent> events) {
        IncomeResolver.Resolution resolution = incomeResolver.resolve(state);
        events.addAll(resolution.events());
        return awaitTargetsOrBuild(resolution.state(), resolution.pendingTargets(), events);
    }

    private static GameState awaitTargetsOrBuild(GameState state, List<Establishment> pending,
                                                 List<GameEvent> events) {
        GameState updated = state.withTurn(state.turn().withPendingTargets(pending));
        if (pending.isEmpty()) {
            return updated.withPhase(Phase.BUILD);
        }
        events.add(new TargetRequired(state.activePlayer().id(), pending.getFirst()));
        return updated.withPhase(Phase.AWAITING_TARGET);
    }

    private static GameState chooseTarget(GameState state, ChooseTarget choice, List<GameEvent> events) {
        List<Establishment> pending = state.turn().pendingTargets();
        Establishment card = pending.getFirst();
        GameState resolved = switch (card) {
            case TV_STATION -> resolveTvStation(state, choice, events);
            case BUSINESS_CENTER -> resolveBusinessCenter(state, choice, events);
            default -> throw new IllegalStateException("Card does not take a target: " + card);
        };
        return awaitTargetsOrBuild(resolved, pending.subList(1, pending.size()), events);
    }

    private static GameState resolveTvStation(GameState state, ChooseTarget choice, List<GameEvent> events) {
        PlayerState target = requireOpponent(state, choice.targetPlayerId());
        int amount = IncomeResolver.tvStationAmount(target);
        if (amount == 0) {
            return state;
        }
        String activeId = state.activePlayer().id();
        events.add(new CoinsTransferred(target.id(), activeId, Establishment.TV_STATION, amount));
        return IncomeResolver.transfer(state, target.id(), activeId, amount);
    }

    private static GameState resolveBusinessCenter(GameState state, ChooseTarget choice,
                                                   List<GameEvent> events) {
        if (choice.targetPlayerId() == null) {
            return state;
        }
        PlayerState target = requireOpponent(state, choice.targetPlayerId());
        PlayerState active = state.activePlayer();
        requireSwappable(active, choice.give());
        requireSwappable(target, choice.take());

        events.add(new EstablishmentsSwapped(active.id(), target.id(), choice.give(), choice.take()));
        return state
                .withPlayer(active.removeEstablishment(choice.give()).addEstablishment(choice.take()))
                .withPlayer(target.removeEstablishment(choice.take()).addEstablishment(choice.give()));
    }

    private static PlayerState requireOpponent(GameState state, String targetPlayerId) {
        if (targetPlayerId == null) {
            throw new IllegalMoveException(RuleViolation.INVALID_TARGET, "Target player is required");
        }
        PlayerState target = state.findPlayer(targetPlayerId)
                .orElseThrow(() -> new IllegalMoveException(RuleViolation.INVALID_TARGET,
                        "Unknown target player: " + targetPlayerId));
        if (target.id().equals(state.activePlayer().id())) {
            throw new IllegalMoveException(RuleViolation.INVALID_TARGET, "Cannot target yourself");
        }
        return target;
    }

    private static void requireSwappable(PlayerState owner, Establishment card) {
        if (card == null || card.isPurple() || owner.count(card) == 0) {
            throw new IllegalMoveException(RuleViolation.INVALID_SWAP,
                    owner.id() + " has no non-purple " + card + " to swap");
        }
    }

    private static GameState build(GameState state, BuildTarget target, List<GameEvent> events) {
        PlayerState player = state.activePlayer();
        if (player.coins() < target.cost()) {
            throw new IllegalMoveException(RuleViolation.NOT_ENOUGH_COINS,
                    target + " costs " + target.cost() + ", player has " + player.coins());
        }
        PlayerState paid = player.withCoins(player.coins() - target.cost());

        GameState built = switch (target) {
            case BuildTarget.EstablishmentTarget(Establishment card) -> {
                if (state.supplyOf(card) == 0) {
                    throw new IllegalMoveException(RuleViolation.SUPPLY_EMPTY, card + " is sold out");
                }
                if (card.isPurple() && player.count(card) > 0) {
                    throw new IllegalMoveException(RuleViolation.PURPLE_ALREADY_OWNED,
                            "Only one " + card + " per player");
                }
                Map<Establishment, Integer> supply = new EnumMap<>(Establishment.class);
                supply.putAll(state.supply());
                supply.merge(card, -1, Integer::sum);
                yield state.withPlayer(paid.addEstablishment(card)).withSupply(supply);
            }
            case BuildTarget.LandmarkTarget(Landmark landmark) -> {
                if (player.has(landmark)) {
                    throw new IllegalMoveException(RuleViolation.LANDMARK_ALREADY_BUILT,
                            landmark + " is already built");
                }
                yield state.withPlayer(paid.addLandmark(landmark));
            }
        };
        events.add(new Built(player.id(), target));
        built = built.withTurn(built.turn().withBuiltThisTurn());

        if (built.activePlayer().hasAllLandmarks()) {
            events.add(new GameWon(player.id()));
            return built.withWinner(player.id());
        }
        return endTurn(built, events);
    }

    /** Amusement Park + doubles keeps the turn with the same player; otherwise play passes clockwise. */
    private static GameState endTurn(GameState state, List<GameEvent> events) {
        PlayerState active = state.activePlayer();
        GameState reset = state.withPhase(Phase.ROLL).withTurn(TurnContext.start());
        if (active.has(Landmark.AMUSEMENT_PARK) && state.turn().isDouble()) {
            events.add(new ExtraTurnGranted(active.id()));
            return reset;
        }
        int nextIndex = (state.activePlayerIndex() + 1) % state.players().size();
        events.add(new TurnPassed(active.id(), state.players().get(nextIndex).id()));
        return reset.withActivePlayerIndex(nextIndex);
    }
}
