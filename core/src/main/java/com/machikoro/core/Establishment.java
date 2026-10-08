package com.machikoro.core;

import java.util.Set;

/**
 * The 15 establishments of the base game.
 */
public enum Establishment {
    WHEAT_FIELD(CardColor.BLUE, CardIcon.WHEAT, 1, 1),
    RANCH(CardColor.BLUE, CardIcon.COW, 1, 2),
    BAKERY(CardColor.GREEN, CardIcon.BREAD, 1, 2, 3),
    CAFE(CardColor.RED, CardIcon.CUP, 2, 3),
    CONVENIENCE_STORE(CardColor.GREEN, CardIcon.BREAD, 2, 4),
    FOREST(CardColor.BLUE, CardIcon.GEAR, 3, 5),
    STADIUM(CardColor.PURPLE, CardIcon.TOWER, 6, 6),
    TV_STATION(CardColor.PURPLE, CardIcon.TOWER, 7, 6),
    BUSINESS_CENTER(CardColor.PURPLE, CardIcon.TOWER, 8, 6),
    CHEESE_FACTORY(CardColor.GREEN, CardIcon.FACTORY, 5, 7),
    FURNITURE_FACTORY(CardColor.GREEN, CardIcon.FACTORY, 3, 8),
    MINE(CardColor.BLUE, CardIcon.GEAR, 6, 9),
    FAMILY_RESTAURANT(CardColor.RED, CardIcon.CUP, 3, 9, 10),
    APPLE_ORCHARD(CardColor.BLUE, CardIcon.WHEAT, 3, 10),
    FRUIT_AND_VEGETABLE_MARKET(CardColor.GREEN, CardIcon.FRUIT, 2, 11, 12);

    private static final int REGULAR_SUPPLY = 6;
    private static final int PURPLE_SUPPLY = 4;

    private final CardColor color;
    private final CardIcon icon;
    private final int cost;
    private final Set<Integer> activationNumbers;
    private final int supplyCount;

    Establishment(CardColor color, CardIcon icon, int cost, Integer... activationNumbers) {
        this.color = color;
        this.icon = icon;
        this.cost = cost;
        this.activationNumbers = Set.of(activationNumbers);
        this.supplyCount = color == CardColor.PURPLE ? PURPLE_SUPPLY : REGULAR_SUPPLY;
    }

    public CardColor color() {
        return color;
    }

    public CardIcon icon() {
        return icon;
    }

    public int cost() {
        return cost;
    }

    public Set<Integer> activationNumbers() {
        return activationNumbers;
    }

    /** Number of copies in the bank at the start of the game. */
    public int supplyCount() {
        return supplyCount;
    }

    public boolean activatesOn(int diceSum) {
        return activationNumbers.contains(diceSum);
    }

    public boolean isPurple() {
        return color == CardColor.PURPLE;
    }
}
