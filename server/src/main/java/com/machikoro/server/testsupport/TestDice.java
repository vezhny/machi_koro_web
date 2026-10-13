package com.machikoro.server.testsupport;

import com.machikoro.core.engine.RandomSource;
import com.machikoro.core.engine.SeededRandomSource;
import com.machikoro.server.game.DiceFactory;
import com.machikoro.server.game.LobbyError;
import com.machikoro.server.game.LobbyException;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Scripted dice for end-to-end tests ({@code test-dice} profile only). Values pushed through
 * {@link TestDiceController} are rolled first, by whichever game rolls next; when the queue is
 * empty a game falls back to its seeded dice.
 */
@Component
@Primary
@Profile(TestDice.PROFILE)
public class TestDice implements DiceFactory {

    public static final String PROFILE = "test-dice";

    private final Deque<Integer> queue = new ConcurrentLinkedDeque<>();

    @Override
    public RandomSource create(long seed) {
        RandomSource fallback = new SeededRandomSource(seed);
        return () -> {
            Integer scripted = queue.pollFirst();
            return scripted != null ? scripted : fallback.rollDie();
        };
    }

    void push(List<Integer> values) {
        for (Integer value : values) {
            if (value == null || value < 1 || value > 6) {
                throw new LobbyException(LobbyError.BAD_REQUEST, "Die values must be 1-6, got " + value);
            }
        }
        queue.addAll(values);
    }

    void clear() {
        queue.clear();
    }

    int remaining() {
        return queue.size();
    }
}
