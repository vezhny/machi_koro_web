package com.machikoro.core.engine;

import java.util.Random;

/**
 * Reproducible dice: the same seed yields the same sequence. Lets a game be replayed from its
 * seed and command log.
 */
public final class SeededRandomSource implements RandomSource {

    private final long seed;
    private final Random random;

    public SeededRandomSource(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    @Override
    public int rollDie() {
        return random.nextInt(6) + 1;
    }

    public long seed() {
        return seed;
    }
}
