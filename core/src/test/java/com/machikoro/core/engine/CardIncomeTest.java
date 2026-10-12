package com.machikoro.core.engine;

import static com.machikoro.core.Establishment.APPLE_ORCHARD;
import static com.machikoro.core.Establishment.BAKERY;
import static com.machikoro.core.Establishment.CAFE;
import static com.machikoro.core.Establishment.CHEESE_FACTORY;
import static com.machikoro.core.Establishment.CONVENIENCE_STORE;
import static com.machikoro.core.Establishment.FAMILY_RESTAURANT;
import static com.machikoro.core.Establishment.FOREST;
import static com.machikoro.core.Establishment.FRUIT_AND_VEGETABLE_MARKET;
import static com.machikoro.core.Establishment.FURNITURE_FACTORY;
import static com.machikoro.core.Establishment.MINE;
import static com.machikoro.core.Establishment.RANCH;
import static com.machikoro.core.Establishment.WHEAT_FIELD;
import static com.machikoro.core.engine.Fixtures.game;
import static com.machikoro.core.engine.Fixtures.player;
import static com.machikoro.core.engine.Fixtures.roll;
import static org.assertj.core.api.Assertions.assertThat;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.Phase;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every blue, green and red card: who rolls what, who owns it, coins before and after.
 * Two players with 5 coins each; p1 is active and owns Train Station so it may roll two dice.
 */
class CardIncomeTest {

    private static final int START_COINS = 5;

    static Stream<Arguments> cases() {
        return Stream.of(
                // name, p1 cards, p2 cards, dice, p1 coins after, p2 coins after
                arguments("Wheat Field on own roll", Map.of(WHEAT_FIELD, 1), Map.of(), dice(1), 6, 5),
                arguments("Wheat Field on opponent roll", Map.of(), Map.of(WHEAT_FIELD, 1), dice(1), 5, 6),
                arguments("each copy activates", Map.of(WHEAT_FIELD, 2), Map.of(WHEAT_FIELD, 3), dice(1), 7, 8),
                arguments("Ranch for everyone", Map.of(RANCH, 1), Map.of(RANCH, 1), dice(2), 6, 6),
                arguments("Bakery on 2, owner only", Map.of(BAKERY, 1), Map.of(BAKERY, 1), dice(2), 6, 5),
                arguments("Bakery on 3", Map.of(BAKERY, 2), Map.of(), dice(3), 7, 5),
                arguments("Cafe takes from active, own Cafe idle", Map.of(CAFE, 1), Map.of(CAFE, 1), dice(3), 4, 6),
                arguments("two Cafes take 2", Map.of(), Map.of(CAFE, 2), dice(3), 3, 7),
                arguments("Convenience Store", Map.of(CONVENIENCE_STORE, 1), Map.of(CONVENIENCE_STORE, 1), dice(4), 8, 5),
                arguments("Forest for everyone", Map.of(FOREST, 1), Map.of(FOREST, 1), dice(5), 6, 6),
                arguments("Cheese Factory per cow", Map.of(CHEESE_FACTORY, 1, RANCH, 2), Map.of(), dice(3, 4), 11, 5),
                arguments("Cheese Factory without cows", Map.of(CHEESE_FACTORY, 1), Map.of(), dice(3, 4), 5, 5),
                arguments("Furniture Factory per gear", Map.of(FURNITURE_FACTORY, 1, FOREST, 1, MINE, 1), Map.of(),
                        dice(4, 4), 11, 5),
                arguments("Mine for everyone", Map.of(MINE, 1), Map.of(MINE, 1), dice(4, 5), 10, 10),
                arguments("Family Restaurant on 9", Map.of(), Map.of(FAMILY_RESTAURANT, 1), dice(4, 5), 3, 7),
                arguments("Family Restaurant on 10", Map.of(), Map.of(FAMILY_RESTAURANT, 1), dice(5, 5), 3, 7),
                arguments("Apple Orchard for everyone", Map.of(APPLE_ORCHARD, 1), Map.of(APPLE_ORCHARD, 1),
                        dice(4, 6), 8, 8),
                arguments("Market on 11 per wheat", Map.of(FRUIT_AND_VEGETABLE_MARKET, 1, WHEAT_FIELD, 1,
                        APPLE_ORCHARD, 1), Map.of(), dice(5, 6), 9, 5),
                arguments("Market on 12", Map.of(FRUIT_AND_VEGETABLE_MARKET, 1, WHEAT_FIELD, 1), Map.of(),
                        dice(6, 6), 7, 5),
                arguments("opponent Market idle", Map.of(), Map.of(FRUIT_AND_VEGETABLE_MARKET, 1, WHEAT_FIELD, 1),
                        dice(6, 6), 5, 5),
                arguments("nothing matches", Map.of(WHEAT_FIELD, 1), Map.of(BAKERY, 1), dice(6), 5, 5));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void cardPaysOut(String name, Map<Establishment, Integer> p1Cards, Map<Establishment, Integer> p2Cards,
                     int[] dice, int p1After, int p2After) {
        GameState start = game(
                player("p1", START_COINS, p1Cards, Landmark.TRAIN_STATION),
                player("p2", START_COINS, p2Cards));

        GameState after = roll(start, dice).state();

        assertThat(after.phase()).isEqualTo(Phase.BUILD);
        assertThat(after.player("p1").coins()).as("p1 coins").isEqualTo(p1After);
        assertThat(after.player("p2").coins()).as("p2 coins").isEqualTo(p2After);
    }

    private static int[] dice(int... values) {
        return values;
    }

    private static Arguments arguments(Object... values) {
        return Arguments.of(values);
    }
}
