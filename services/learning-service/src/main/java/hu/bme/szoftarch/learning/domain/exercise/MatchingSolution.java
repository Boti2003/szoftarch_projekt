package hu.bme.szoftarch.learning.domain.exercise;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Párosítós feladat megoldása (TERV 5.1): a helyes bal-jobb ID-párok.
 *
 * @param pairs a helyes párok listája
 */
public record MatchingSolution(
        @NotEmpty @Valid List<Pair> pairs) implements ExerciseSolution {

    /**
     * Egy helyes párosítás.
     *
     * @param leftId  a bal oldali elem azonosítója
     * @param rightId a hozzá tartozó jobb oldali elem azonosítója
     */
    public record Pair(@NotBlank String leftId, @NotBlank String rightId) {
    }
}
