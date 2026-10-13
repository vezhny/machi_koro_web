package com.machikoro.core.engine;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Returns a predefined sequence of die values. For tests and deterministic scenarios.
 */
public final class FixedRandomSource implements RandomSource {

    private final Deque<Integer> values = new ArrayDeque<>();

    public FixedRandomSource(int... values) {
        for (int value : values) {
            if (value < 1 || value > 6) {
                throw new IllegalArgumentException("Die value out of range: " + value);
            }
            this.values.addLast(value);
        }
    }

    /**
     * @throws IllegalStateException when the sequence is exhausted
     */
    @Override
    public int rollDie() {
        Integer value = values.pollFirst();
        if (value == null) {
            throw new IllegalStateException("No more predefined die values");
        }
        return value;
    }

    public int remaining() {
        return values.size();
    }
}
