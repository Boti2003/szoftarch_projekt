package hu.bme.szoftarch.learning.domain.exercise;

import jakarta.validation.constraints.NotBlank;

/**
 * Kakukktojás feladat megoldása (TERV 5.1): a kilógó elem azonosítója.
 *
 * @param oddItemId a kilógó {@code Item.id} értéke
 */
public record OddOneOutSolution(
        @NotBlank String oddItemId) implements ExerciseSolution {
}
