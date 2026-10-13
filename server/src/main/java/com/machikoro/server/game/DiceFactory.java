package com.machikoro.server.game;

import com.machikoro.core.engine.RandomSource;

/**
 * Creates the dice for a new game. Replaceable in tests and deterministic profiles.
 */
@FunctionalInterface
public interface DiceFactory {

    RandomSource create(long seed);
}
