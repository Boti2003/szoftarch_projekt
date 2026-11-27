package hu.bme.szoftarch.learning.domain.exercise;

/**
 * Igaz-hamis feladat megoldása (TERV 5.1): az állítás igaz vagy hamis.
 *
 * @param answer a helyes érték: {@code true} ha az állítás igaz
 */
public record TrueFalseSolution(boolean answer) implements ExerciseSolution {
}
