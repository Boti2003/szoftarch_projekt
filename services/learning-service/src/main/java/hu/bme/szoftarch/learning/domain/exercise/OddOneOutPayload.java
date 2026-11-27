package hu.bme.szoftarch.learning.domain.exercise;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Kakukktojás feladat payloadja (TERV 5.1 {@code ODD_ONE_OUT}): a választható
 * elemek. A kilógó elem ID-ja a {@link OddOneOutSolution}-ben van, nem itt.
 *
 * @param items a választható elemek (legalább három)
 */
public record OddOneOutPayload(
        @NotEmpty @Size(min = 3) @Valid List<Item> items) implements ExercisePayload {

    /**
     * Egy választható elem.
     *
     * @param id   egyedi elemazonosító a feladaton belül
     * @param text a megjelenített szöveg
     */
    public record Item(@NotBlank String id, @NotBlank String text) {
    }
}
