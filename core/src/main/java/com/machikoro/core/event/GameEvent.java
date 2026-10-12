package com.machikoro.core.event;

/**
 * Something that happened while applying a command. Used by clients for animations and the log.
 */
public sealed interface GameEvent permits DiceRolled, IncomeReceived, CoinsTransferred, TargetRequired,
        EstablishmentsSwapped, Built, TurnPassed, ExtraTurnGranted, GameWon {
}
