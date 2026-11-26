package hu.bme.szoftarch.ai.infrastructure.openai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * A nyelvi modelltől elvárt fordítás-értékelés kimenete. A Bean Validation
 * annotációk alkotják a kimenet sémáját; ha a modell nem ezt adja, a hívás
 * sémahibának minősül.
 *
 * @param score    0.0–1.0 közötti pontszám
 * @param feedback a tanulónak szóló visszajelzés
 */
public record TranslationOutput(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double score,
        @NotNull String feedback) {
}
