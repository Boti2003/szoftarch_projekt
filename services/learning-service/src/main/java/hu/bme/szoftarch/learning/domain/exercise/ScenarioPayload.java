package hu.bme.szoftarch.learning.domain.exercise;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * AI-szituáció feladat payloadja (TERV 5.1 {@code AI_SCENARIO}): a szerep, a
 * helyzet, a célok, a célnyelv és a kezdőüzenet. Többfordulós AI-típus, nincs
 * egzakt megoldás, ezért nincs hozzá {@link ExerciseSolution}.
 *
 * @param role               az AI által megszemélyesített szerep
 * @param situation          a szituáció leírása
 * @param goals              a felhasználó teljesítendő céljai
 * @param targetLanguageCode a célnyelv kódja (pl. {@code en})
 * @param openingMessage     az AI kezdőüzenete
 */
public record ScenarioPayload(
        @NotBlank String role,
        @NotBlank String situation,
        @NotEmpty List<@NotBlank String> goals,
        @NotBlank String targetLanguageCode,
        @NotBlank String openingMessage) implements ExercisePayload {
}
