package com.machikoro.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomSourceTest {

    @Test
    void secureSourceRollsAllFacesWithinRange() {
        SecureRandomSource source = new SecureRandomSource();
        Set<Integer> seen = new HashSet<>();

        for (int i = 0; i < 1_000; i++) {
            int value = source.rollDie();
            assertThat(value).isBetween(1, 6);
            seen.add(value);
        }

        assertThat(seen).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
    }

    @Test
    void seededSourceIsReproducible() {
        SeededRandomSource first = new SeededRandomSource(99L);
        SeededRandomSource second = new SeededRandomSource(99L);

        assertThat(first.seed()).isEqualTo(99L);
        for (int i = 0; i < 100; i++) {
            int value = first.rollDie();
            assertThat(value).isBetween(1, 6).isEqualTo(second.rollDie());
        }
    }

    @Test
    void fixedSourceReturnsValuesInOrder() {
        FixedRandomSource source = new FixedRandomSource(3, 6, 1);

        assertThat(source.remaining()).isEqualTo(3);
        assertThat(source.rollDie()).isEqualTo(3);
        assertThat(source.rollDie()).isEqualTo(6);
        assertThat(source.rollDie()).isEqualTo(1);
        assertThat(source.remaining()).isZero();
    }

    @Test
    void fixedSourceFailsWhenExhausted() {
        FixedRandomSource source = new FixedRandomSource();

        assertThatThrownBy(source::rollDie).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fixedSourceRejectsImpossibleValues() {
        assertThatThrownBy(() -> new FixedRandomSource(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedRandomSource(7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void illegalMoveCarriesCode() {
        IllegalMoveException e = new IllegalMoveException(RuleViolation.SUPPLY_EMPTY, "sold out");

        assertThat(e.code()).isEqualTo(RuleViolation.SUPPLY_EMPTY);
        assertThat(e).hasMessage("sold out");
        assertThatThrownBy(() -> new IllegalMoveException(null, "x")).isInstanceOf(NullPointerException.class);
    }
}
