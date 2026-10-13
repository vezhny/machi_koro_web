package com.machikoro.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of one player's city and purse.
 *
 * @param establishments copies owned per establishment; absent key means zero
 */
public record PlayerState(
        String id,
        String name,
        int coins,
        Map<Establishment, Integer> establishments,
        Set<Landmark> landmarks) {

    public PlayerState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (coins < 0) {
            throw new IllegalArgumentException("Coins must not be negative: " + coins);
        }
        establishments = EnumCollections.countsCopy(Establishment.class, establishments);
        landmarks = EnumCollections.setCopy(Landmark.class, landmarks);
    }

    public PlayerState withCoins(int newCoins) {
        return new PlayerState(id, name, newCoins, establishments, landmarks);
    }

    public PlayerState addEstablishment(Establishment establishment) {
        Map<Establishment, Integer> updated = new EnumMap<>(Establishment.class);
        updated.putAll(establishments);
        updated.merge(establishment, 1, Integer::sum);
        return new PlayerState(id, name, coins, updated, landmarks);
    }

    /**
     * @throws IllegalStateException if the player does not own {@code establishment}
     */
    public PlayerState removeEstablishment(Establishment establishment) {
        if (count(establishment) == 0) {
            throw new IllegalStateException(id + " does not own " + establishment);
        }
        Map<Establishment, Integer> updated = new EnumMap<>(Establishment.class);
        updated.putAll(establishments);
        updated.merge(establishment, -1, Integer::sum);
        return new PlayerState(id, name, coins, updated, landmarks);
    }

    public PlayerState addLandmark(Landmark landmark) {
        Set<Landmark> updated = EnumSet.noneOf(Landmark.class);
        updated.addAll(landmarks);
        updated.add(landmark);
        return new PlayerState(id, name, coins, establishments, updated);
    }

    public int count(Establishment establishment) {
        return establishments.getOrDefault(establishment, 0);
    }

    public boolean has(Landmark landmark) {
        return landmarks.contains(landmark);
    }

    /** Total copies of establishments with the given icon. */
    public int countIcon(CardIcon icon) {
        return establishments.entrySet().stream()
                .filter(entry -> entry.getKey().icon() == icon)
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public boolean hasAllLandmarks() {
        return landmarks.size() == Landmark.values().length;
    }
}
