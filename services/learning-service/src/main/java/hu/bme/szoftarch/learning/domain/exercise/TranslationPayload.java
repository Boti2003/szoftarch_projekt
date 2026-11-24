package hu.bme.szoftarch.learning.domain.exercise;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Fordítós feladat payloadja (TERV 5.1 {@code TRANSLATION}): a forrásszöveg, a
 * célnyelv és az AI-értékelés szempontjai. AI-típus, nincs egzakt megoldás,
 * ezért nincs hozzá {@link ExerciseSolution}; a payload a prezentációhoz kell.
 *
 * @param sourceText         a lefordítandó forrásszöveg
 * @param targetLanguageCode a célnyelv kódja (pl. {@code en})
 * @param rubric             az AI-értékelés szempontjai (prezentációhoz is)
 */
public record TranslationPayload(
        @NotBlank String sourceText,
        @NotBlank String targetLanguageCode,
        @NotNull List<@NotBlank String> rubric) implements ExercisePayload {
}
