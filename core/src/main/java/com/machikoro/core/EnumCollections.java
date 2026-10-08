package com.machikoro.core;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Unmodifiable enum-keyed copies with stable (declaration) iteration order.
 */
final class EnumCollections {

    private EnumCollections() {
    }

    /** Copies {@code source}, dropping zero counts and rejecting negative ones. */
    static <E extends Enum<E>> Map<E, Integer> countsCopy(Class<E> type, Map<E, Integer> source) {
        EnumMap<E, Integer> copy = new EnumMap<>(type);
        source.forEach((key, count) -> {
            if (count < 0) {
                throw new IllegalArgumentException("Negative count for " + key + ": " + count);
            }
            if (count > 0) {
                copy.put(key, count);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    static <E extends Enum<E>> Set<E> setCopy(Class<E> type, Collection<E> source) {
        EnumSet<E> copy = EnumSet.noneOf(type);
        copy.addAll(source);
        return Collections.unmodifiableSet(copy);
    }
}
