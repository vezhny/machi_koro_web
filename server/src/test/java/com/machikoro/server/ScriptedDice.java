package com.machikoro.server;

import com.machikoro.core.engine.RandomSource;
import com.machikoro.server.game.DiceFactory;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;

/**
 * Dice shared by all games of the test context; tests push the values they need.
 */
@TestConfiguration(proxyBeanMethods = false)
@Primary
public class ScriptedDice implements DiceFactory, RandomSource {

    private final Deque<Integer> values = new ConcurrentLinkedDeque<>();

    public void push(int... dice) {
        for (int value : dice) {
            values.addLast(value);
        }
    }

    public void clear() {
        values.clear();
    }

    @Override
    public RandomSource create(long seed) {
        return this;
    }

    @Override
    public int rollDie() {
        Integer value = values.pollFirst();
        if (value == null) {
            throw new IllegalStateException("Test did not script enough dice");
        }
        return value;
    }
}
