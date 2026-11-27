package hu.bme.szoftarch.learning.domain.exercise;

import java.util.List;
import java.util.Objects;

/**
 * A felhasználó párosítós válasza (U3): a megadott bal-jobb ID-párok.
 *
 * @param pairs a felhasználó párosításai; lehet üres
 */
public record MatchingAnswer(List<Pair> pairs) implements UserAnswer {

    public MatchingAnswer {
        pairs = pairs == null ? List.of() : List.copyOf(pairs);
    }

    /**
     * Egy felhasználói párosítás.
     *
     * @param leftId  a bal oldali elem azonosítója
     * @param rightId a hozzá rendelt jobb oldali elem azonosítója
     */
    public record Pair(String leftId, String rightId) {
        public Pair {
            Objects.requireNonNull(leftId, "leftId");
            Objects.requireNonNull(rightId, "rightId");
        }
    }
}
