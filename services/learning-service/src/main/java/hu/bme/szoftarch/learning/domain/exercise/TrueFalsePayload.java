package hu.bme.szoftarch.learning.domain.exercise;

import jakarta.validation.constraints.NotBlank;

/**
 * Igaz-hamis feladat payloadja (TERV 5.1 {@code TRUE_FALSE}): a megítélendő
 * állítás. A feleletválasztós speciális esete. A helyes érték (igaz/hamis) a
 * {@link TrueFalseSolution}-ben van, nem itt.
 *
 * @param statement a megítélendő állítás szövege
 */
public record TrueFalsePayload(
        @NotBlank String statement) implements ExercisePayload {
}
