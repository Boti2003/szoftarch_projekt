package hu.bme.szoftarch.learning.domain.exercise;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

/**
 * Feleletválasztós feladat megoldása (TERV 5.1): a helyes opciók ID-i.
 *
 * @param correctOptionIds a helyes {@code Option.id} értékek halmaza
 */
public record MultipleChoiceSolution(
        @NotEmpty Set<String> correctOptionIds) implements ExerciseSolution {
}
