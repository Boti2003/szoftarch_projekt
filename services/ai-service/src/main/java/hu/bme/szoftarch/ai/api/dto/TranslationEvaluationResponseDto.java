package hu.bme.szoftarch.ai.api.dto;

/**
 * A fordítás-értékelés válasza.
 *
 * @param score    0.0–1.0 közötti pontszám
 * @param accepted elfogadva-e
 * @param feedback szöveges visszajelzés a tanulónak
 */
public record TranslationEvaluationResponseDto(double score, boolean accepted, String feedback) {
}
