package hu.bme.szoftarch.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * A belső fordítás-értékelés kérés törzse. A hívó service küldi a megbízható
 * kontextust és a tanuló fordítását.
 */
public record TranslationEvaluationRequestDto(
        String sourceText,
        String targetLanguage,
        @NotBlank(message = "A learnerTranslation nem lehet üres")
        String learnerTranslation,
        List<String> referenceTranslations,
        List<String> evaluationCriteria) {
}
