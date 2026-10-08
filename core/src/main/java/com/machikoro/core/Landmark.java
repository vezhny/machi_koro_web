package com.machikoro.core;

/**
 * The 4 landmarks of the base game. Building all of them wins the game.
 */
public enum Landmark {
    /** Roll 1 or 2 dice. */
    TRAIN_STATION(4),
    /** +1 income for each own BREAD and CUP card. */
    SHOPPING_MALL(10),
    /** Rolling doubles grants an extra turn. */
    AMUSEMENT_PARK(16),
    /** One reroll per turn. */
    RADIO_TOWER(22);

    private final int cost;

    Landmark(int cost) {
        this.cost = cost;
    }

    public int cost() {
        return cost;
    }
}
