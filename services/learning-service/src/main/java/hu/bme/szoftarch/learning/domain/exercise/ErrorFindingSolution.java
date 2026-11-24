package hu.bme.szoftarch.learning.domain.exercise;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

/**
 * Hibakereső feladat megoldása (TERV 5.1): a hibás tokenek indexeinek halmaza.
 *
 * @param incorrectTokenIndexes a hibásnak jelölendő tokenindexek
 */
public record ErrorFindingSolution(
        @NotEmpty Set<Integer> incorrectTokenIndexes) implements ExerciseSolution {
}
