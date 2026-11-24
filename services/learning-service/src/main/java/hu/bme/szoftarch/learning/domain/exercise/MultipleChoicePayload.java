package hu.bme.szoftarch.learning.domain.exercise;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Feleletválasztós feladat payloadja (TERV 5.1 {@code MULTIPLE_CHOICE}): a
 * választható opciók (azonosító + megjelenített szöveg). A helyes option
 * ID-k a {@link MultipleChoiceSolution}-ben vannak, nem itt.
 *
 * @param multiSelect ha {@code true}, több helyes válasz is lehetséges
 * @param options     a választható opciók (legalább kettő)
 */
public record MultipleChoicePayload(
        boolean multiSelect,
        @NotEmpty @Size(min = 2) @Valid List<Option> options) implements ExercisePayload {

    /**
     * Egy választható opció.
     *
     * @param id   egyedi opcióazonosító a feladaton belül
     * @param text a megjelenített szöveg
     */
    public record Option(@NotBlank String id, @NotBlank String text) {
    }
}
