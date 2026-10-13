package com.machikoro.core;

import java.util.List;
import java.util.Objects;

/**
 * Data of the current turn.
 *
 * @param dice           values of the last roll; empty before the first roll of the turn
 * @param rerollUsed     Radio Tower reroll has been spent this turn
 * @param builtThisTurn  the active player has already bought a card this turn
 * @param pendingTargets purple cards (TV Station, Business Center) still waiting for the
 *                       active player to choose a target, in resolution order
 */
public record TurnContext(
        List<Integer> dice,
        boolean rerollUsed,
        boolean builtThisTurn,
        List<Establishment> pendingTargets) {

    private static final TurnContext START = new TurnContext(List.of(), false, false, List.of());

    public TurnContext {
        dice = List.copyOf(Objects.requireNonNull(dice, "dice"));
        pendingTargets = List.copyOf(Objects.requireNonNull(pendingTargets, "pendingTargets"));
        for (int die : dice) {
            if (die < 1 || die > 6) {
                throw new IllegalArgumentException("Die value out of range: " + die);
            }
        }
    }

    /** Context at the beginning of a turn, before any roll. */
    public static TurnContext start() {
        return START;
    }

    public TurnContext withDice(List<Integer> newDice) {
        return new TurnContext(newDice, rerollUsed, builtThisTurn, pendingTargets);
    }

    public TurnContext withRerollUsed() {
        return new TurnContext(dice, true, builtThisTurn, pendingTargets);
    }

    public TurnContext withBuiltThisTurn() {
        return new TurnContext(dice, rerollUsed, true, pendingTargets);
    }

    public TurnContext withPendingTargets(List<Establishment> targets) {
        return new TurnContext(dice, rerollUsed, builtThisTurn, targets);
    }

    public boolean rolled() {
        return !dice.isEmpty();
    }

    public int diceSum() {
        return dice.stream().mapToInt(Integer::intValue).sum();
    }

    /** Two dice showing the same value. */
    public boolean isDouble() {
        return dice.size() == 2 && dice.get(0).equals(dice.get(1));
    }
}
