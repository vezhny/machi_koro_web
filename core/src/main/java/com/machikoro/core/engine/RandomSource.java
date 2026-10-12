package com.machikoro.core.engine;

/**
 * Source of die rolls.
 */
public interface RandomSource {

    /** @return a value from 1 to 6 */
    int rollDie();
}
