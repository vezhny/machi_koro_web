package com.machikoro.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Card parameters must match the rules table of the base game.
 */
class CardCatalogTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "WHEAT_FIELD,                BLUE,   WHEAT,   1, 1,     6",
            "RANCH,                      BLUE,   COW,     1, 2,     6",
            "BAKERY,                     GREEN,  BREAD,   1, 2 3,   6",
            "CAFE,                       RED,    CUP,     2, 3,     6",
            "CONVENIENCE_STORE,          GREEN,  BREAD,   2, 4,     6",
            "FOREST,                     BLUE,   GEAR,    3, 5,     6",
            "STADIUM,                    PURPLE, TOWER,   6, 6,     4",
            "TV_STATION,                 PURPLE, TOWER,   7, 6,     4",
            "BUSINESS_CENTER,            PURPLE, TOWER,   8, 6,     4",
            "CHEESE_FACTORY,             GREEN,  FACTORY, 5, 7,     6",
            "FURNITURE_FACTORY,          GREEN,  FACTORY, 3, 8,     6",
            "MINE,                       BLUE,   GEAR,    6, 9,     6",
            "FAMILY_RESTAURANT,          RED,    CUP,     3, 9 10,  6",
            "APPLE_ORCHARD,              BLUE,   WHEAT,   3, 10,    6",
            "FRUIT_AND_VEGETABLE_MARKET, GREEN,  FRUIT,   2, 11 12, 6",
    })
    void establishmentMatchesRules(Establishment card, CardColor color, CardIcon icon, int cost,
                                   String numbers, int supply) {
        Set<Integer> expectedNumbers = Arrays.stream(numbers.split(" "))
                .map(Integer::valueOf)
                .collect(Collectors.toSet());

        assertThat(card.color()).isEqualTo(color);
        assertThat(card.icon()).isEqualTo(icon);
        assertThat(card.cost()).isEqualTo(cost);
        assertThat(card.activationNumbers()).isEqualTo(expectedNumbers);
        assertThat(card.supplyCount()).isEqualTo(supply);
        assertThat(card.isPurple()).isEqualTo(color == CardColor.PURPLE);
        expectedNumbers.forEach(n -> assertThat(card.activatesOn(n)).isTrue());
    }

    @Test
    void baseGameHasFifteenEstablishments() {
        assertThat(Establishment.values()).hasSize(15);
    }

    @Test
    void activatesOnlyOnItsNumbers() {
        assertThat(Establishment.BAKERY.activatesOn(1)).isFalse();
        assertThat(Establishment.BAKERY.activatesOn(4)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "TRAIN_STATION,  4",
            "SHOPPING_MALL,  10",
            "AMUSEMENT_PARK, 16",
            "RADIO_TOWER,    22",
    })
    void landmarkCostMatchesRules(Landmark landmark, int cost) {
        assertThat(landmark.cost()).isEqualTo(cost);
    }

    @Test
    void baseGameHasFourLandmarks() {
        assertThat(Landmark.values()).hasSize(4);
    }
}
