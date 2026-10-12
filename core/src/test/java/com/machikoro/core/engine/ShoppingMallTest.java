package com.machikoro.core.engine;

import static com.machikoro.core.Establishment.BAKERY;
import static com.machikoro.core.Establishment.CAFE;
import static com.machikoro.core.Establishment.CONVENIENCE_STORE;
import static com.machikoro.core.Establishment.FAMILY_RESTAURANT;
import static com.machikoro.core.Establishment.WHEAT_FIELD;
import static com.machikoro.core.engine.Fixtures.game;
import static com.machikoro.core.engine.Fixtures.player;
import static com.machikoro.core.engine.Fixtures.roll;
import static org.assertj.core.api.Assertions.assertThat;

import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Shopping Mall: +1 per own BREAD and CUP card.
 */
class ShoppingMallTest {

    @Test
    void boostsOwnBreadCards() {
        GameState start = game(
                player("p1", 0, Map.of(BAKERY, 2), Landmark.SHOPPING_MALL),
                player("p2", 0, Map.of()));

        assertThat(roll(start, 2).state().player("p1").coins()).isEqualTo(4);
    }

    @Test
    void boostsConvenienceStore() {
        GameState start = game(
                player("p1", 0, Map.of(CONVENIENCE_STORE, 1), Landmark.SHOPPING_MALL),
                player("p2", 0, Map.of()));

        assertThat(roll(start, 4).state().player("p1").coins()).isEqualTo(4);
    }

    @Test
    void boostsOwnCupCards() {
        GameState start = game(
                player("p1", 10, Map.of(), Landmark.TRAIN_STATION),
                player("p2", 0, Map.of(CAFE, 1, FAMILY_RESTAURANT, 1), Landmark.SHOPPING_MALL));

        assertThat(roll(start, 3).state().player("p2").coins()).isEqualTo(2);
        assertThat(roll(start, 5, 5).state().player("p2").coins()).isEqualTo(3);
    }

    @Test
    void doesNotBoostOtherIcons() {
        GameState start = game(
                player("p1", 0, Map.of(WHEAT_FIELD, 1), Landmark.SHOPPING_MALL),
                player("p2", 0, Map.of()));

        assertThat(roll(start, 1).state().player("p1").coins()).isEqualTo(1);
    }

    @Test
    void activePlayersMallDoesNotBoostOpponentsCafe() {
        GameState start = game(
                player("p1", 5, Map.of(), Landmark.SHOPPING_MALL),
                player("p2", 0, Map.of(CAFE, 1)));

        assertThat(roll(start, 3).state().player("p2").coins()).isEqualTo(1);
    }

    @Test
    void noBoostWithoutMall() {
        GameState start = game(
                player("p1", 0, Map.of(BAKERY, 1)),
                player("p2", 0, Map.of()));

        assertThat(roll(start, 2).state().player("p1").coins()).isEqualTo(1);
    }
}
