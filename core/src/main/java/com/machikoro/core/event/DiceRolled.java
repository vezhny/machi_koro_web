package com.machikoro.core.event;

import java.util.List;

/**
 * @param reroll {@code true} for a Radio Tower reroll
 */
public record DiceRolled(String playerId, List<Integer> dice, boolean reroll) implements GameEvent {

    public DiceRolled {
        dice = List.copyOf(dice);
    }
}
