package hu.bme.szoftarch.learning.domain.exercise;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Párosítós feladat payloadja (TERV 5.1 {@code MATCHING}): a bal és jobb oldali
 * elemek. A helyes ID-párok a {@link MatchingSolution}-ben vannak, nem itt.
 *
 * @param leftItems  a bal oldali elemek
 * @param rightItems a jobb oldali elemek
 */
public record MatchingPayload(
        @NotEmpty @Valid List<Item> leftItems,
        @NotEmpty @Valid List<Item> rightItems) implements ExercisePayload {

    /**
     * Egy párosítható elem.
     *
     * @param id   egyedi elemazonosító a feladaton belül
     * @param text a megjelenített szöveg
     */
    public record Item(@NotBlank String id, @NotBlank String text) {
    }
}
