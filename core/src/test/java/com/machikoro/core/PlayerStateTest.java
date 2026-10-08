package com.machikoro.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerStateTest {

    private final PlayerState start = new PlayerState("p1", "Alice", 3,
            Map.of(Establishment.WHEAT_FIELD, 1, Establishment.BAKERY, 1), Set.of());

    @Test
    void withersReturnNewInstanceAndLeaveOriginalUntouched() {
        PlayerState changed = start
                .withCoins(10)
                .addEstablishment(Establishment.WHEAT_FIELD)
                .addEstablishment(Establishment.CAFE)
                .removeEstablishment(Establishment.BAKERY)
                .addLandmark(Landmark.TRAIN_STATION);

        assertThat(changed.coins()).isEqualTo(10);
        assertThat(changed.establishments())
                .isEqualTo(Map.of(Establishment.WHEAT_FIELD, 2, Establishment.CAFE, 1));
        assertThat(changed.landmarks()).containsExactly(Landmark.TRAIN_STATION);

        assertThat(start.coins()).isEqualTo(3);
        assertThat(start.establishments())
                .isEqualTo(Map.of(Establishment.WHEAT_FIELD, 1, Establishment.BAKERY, 1));
        assertThat(start.landmarks()).isEmpty();
    }

    @Test
    void collectionsCannotBeModifiedFromOutside() {
        assertThatThrownBy(() -> start.establishments().put(Establishment.MINE, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> start.landmarks().add(Landmark.RADIO_TOWER))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructorCopiesInputCollections() {
        Map<Establishment, Integer> cards = new HashMap<>(Map.of(Establishment.RANCH, 1));
        Set<Landmark> landmarks = new HashSet<>();
        PlayerState player = new PlayerState("p1", "Alice", 0, cards, landmarks);

        cards.put(Establishment.MINE, 3);
        landmarks.add(Landmark.SHOPPING_MALL);

        assertThat(player.establishments()).isEqualTo(Map.of(Establishment.RANCH, 1));
        assertThat(player.landmarks()).isEmpty();
    }

    @Test
    void removingLastCopyDropsTheEntry() {
        PlayerState player = start.removeEstablishment(Establishment.BAKERY);

        assertThat(player.count(Establishment.BAKERY)).isZero();
        assertThat(player.establishments()).doesNotContainKey(Establishment.BAKERY);
        assertThat(player).isEqualTo(new PlayerState("p1", "Alice", 3, Map.of(Establishment.WHEAT_FIELD, 1), Set.of()));
    }

    @Test
    void cannotRemoveCardNotOwned() {
        assertThatThrownBy(() -> start.removeEstablishment(Establishment.MINE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> start.withCoins(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerState("p1", "A", 0, Map.of(Establishment.MINE, -1), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerState(null, "A", 0, Map.of(), Set.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void countsCardsByIcon() {
        PlayerState player = new PlayerState("p1", "A", 0,
                Map.of(Establishment.WHEAT_FIELD, 2, Establishment.APPLE_ORCHARD, 1, Establishment.RANCH, 1),
                Set.of());

        assertThat(player.countIcon(CardIcon.WHEAT)).isEqualTo(3);
        assertThat(player.countIcon(CardIcon.COW)).isEqualTo(1);
        assertThat(player.countIcon(CardIcon.GEAR)).isZero();
    }

    @Test
    void knowsWhenAllLandmarksAreBuilt() {
        PlayerState almost = new PlayerState("p1", "A", 0, Map.of(),
                EnumSet.of(Landmark.TRAIN_STATION, Landmark.SHOPPING_MALL, Landmark.AMUSEMENT_PARK));

        assertThat(almost.hasAllLandmarks()).isFalse();
        assertThat(almost.has(Landmark.RADIO_TOWER)).isFalse();
        assertThat(almost.addLandmark(Landmark.RADIO_TOWER).hasAllLandmarks()).isTrue();
    }
}
