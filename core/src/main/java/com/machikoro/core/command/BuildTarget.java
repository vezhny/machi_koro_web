package com.machikoro.core.command;

import com.machikoro.core.Establishment;
import com.machikoro.core.Landmark;
import java.util.Objects;

/**
 * What to build: an establishment from the bank or a landmark.
 */
public sealed interface BuildTarget {

    int cost();

    record EstablishmentTarget(Establishment establishment) implements BuildTarget {

        public EstablishmentTarget {
            Objects.requireNonNull(establishment, "establishment");
        }

        @Override
        public int cost() {
            return establishment.cost();
        }
    }

    record LandmarkTarget(Landmark landmark) implements BuildTarget {

        public LandmarkTarget {
            Objects.requireNonNull(landmark, "landmark");
        }

        @Override
        public int cost() {
            return landmark.cost();
        }
    }

    static BuildTarget of(Establishment establishment) {
        return new EstablishmentTarget(establishment);
    }

    static BuildTarget of(Landmark landmark) {
        return new LandmarkTarget(landmark);
    }
}
