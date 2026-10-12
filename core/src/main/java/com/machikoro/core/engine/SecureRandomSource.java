package com.machikoro.core.engine;

import java.security.SecureRandom;

/**
 * Unpredictable fair dice for real games.
 */
public final class SecureRandomSource implements RandomSource {

    private final SecureRandom random = new SecureRandom();

    @Override
    public int rollDie() {
        return random.nextInt(6) + 1;
    }
}
