package hu.bme.szoftarch.learning.domain.exercise;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Hibakereső feladat payloadja (TERV 5.1 {@code ERROR_FINDING}): a szöveg
 * indexelt tokenjei, amelyek közül a felhasználónak ki kell jelölnie a
 * hibásakat. A hibás tokenindexek halmaza a {@link ErrorFindingSolution}-ben
 * van, nem itt.
 *
 * @param tokens a szöveg tokenjei indexelve
 */
public record ErrorFindingPayload(
        @NotEmpty @Valid List<Token> tokens) implements ExercisePayload {

    /**
     * Egy indexelt token.
     *
     * @param index a token nulla-alapú pozíciója
     * @param text  a token szövege
     */
    public record Token(@PositiveOrZero int index, @NotBlank String text) {
    }
}
