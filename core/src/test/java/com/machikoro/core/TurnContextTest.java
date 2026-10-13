package com.machikoro.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnContextTest {

    @Test
    void startIsEmpty() {
        TurnContext start = TurnContext.start();

        assertThat(start.rolled()).isFalse();
        assertThat(start.diceSum()).isZero();
        assertThat(start.isDouble()).isFalse();
        assertThat(start.rerollUsed()).isFalse();
        assertThat(start.builtThisTurn()).isFalse();
        assertThat(start.pendingTargets()).isEmpty();
    }

    @Test
    void withersReturnNewInstanceAndLeaveOriginalUntouched() {
        TurnContext start = TurnContext.start();
        TurnContext changed = start
                .withDice(List.of(5, 5))
                .withRerollUsed()
                .withBuiltThisTurn()
                .withPendingTargets(List.of(Establishment.TV_STATION, Establishment.BUSINESS_CENTER));

        assertThat(changed.rolled()).isTrue();
        assertThat(changed.diceSum()).isEqualTo(10);
        assertThat(changed.isDouble()).isTrue();
        assertThat(changed.rerollUsed()).isTrue();
        assertThat(changed.builtThisTurn()).isTrue();
        assertThat(changed.pendingTargets())
                .containsExactly(Establishment.TV_STATION, Establishment.BUSINESS_CENTER);

        assertThat(start).isEqualTo(new TurnContext(List.of(), false, false, List.of()));
    }

    @Test
    void doubleNeedsTwoEqualDice() {
        assertThat(TurnContext.start().withDice(List.of(4)).isDouble()).isFalse();
        assertThat(TurnContext.start().withDice(List.of(2, 3)).isDouble()).isFalse();
        assertThat(TurnContext.start().withDice(List.of(6, 6)).isDouble()).isTrue();
    }

    @Test
    void constructorCopiesInputLists() {
        List<Integer> dice = new ArrayList<>(List.of(1, 2));
        TurnContext turn = TurnContext.start().withDice(dice);

        dice.add(3);

        assertThat(turn.dice()).containsExactly(1, 2);
        assertThatThrownBy(() -> turn.dice().add(4)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidDieValues() {
        assertThatThrownBy(() -> TurnContext.start().withDice(List.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TurnContext.start().withDice(List.of(7)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
