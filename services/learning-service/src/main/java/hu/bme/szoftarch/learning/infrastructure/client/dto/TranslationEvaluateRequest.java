package hu.bme.szoftarch.learning.infrastructure.client.dto;

import java.util.List;

/**
 * Fordítós kiértékelés kérés-body-ja az AI belső API-nak
 * ({@code POST /internal/v1/translations:evaluate}, TERV 7.7). Lokális DTO: csak
 * a ténylegesen használt, megbízható feladat-konfiguráció és a felhasználói
 * szöveg (nincs e-mail/felhasználónév/token).
 *
 * <p>INTEGRÁCIÓS ILLESZTÉS (D-vel jóváhagyandó): a mezőnevek az ai-service
 * tényleges {@code TranslationEvaluationRequestDto}-jához igazodnak
 * ({@code targetLanguage}, {@code learnerTranslation}, {@code evaluationCriteria}),
 * mert az AI-oldal már ezekkel implementált ({@code learnerTranslation} @NotBlank).
 * A végleges mezőneveket a közös contractban (ai-internal-api.yaml) kell rögzíteni.</p>
 *
 * @param sourceText           a lefordítandó forrásszöveg
 * @param targetLanguage       a célnyelv kódja
 * @param learnerTranslation   a felhasználó fordítása
 * @param referenceTranslations opcionális referencia-fordítások (üres is lehet)
 * @param evaluationCriteria   az AI-értékelés szempontjai
 */
public record TranslationEvaluateRequest(String sourceText, String targetLanguage,
        String learnerTranslation, List<String> referenceTranslations,
        List<String> evaluationCriteria) {
}
