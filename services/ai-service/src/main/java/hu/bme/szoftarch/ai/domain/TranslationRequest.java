package hu.bme.szoftarch.ai.domain;

import java.util.List;

/**
 * Fordítás-értékelés bemenete (domain value object).
 *
 * <p>A megbízható mezők (forrásszöveg, célnyelv, referencia-megoldások,
 * értékelési szempontok) a hívó service-től érkeznek, nem a böngészőtől.
 * A felhasználó szabad szövege a {@code learnerTranslation}.
 *
 * @param sourceText            az eredeti, lefordítandó szöveg
 * @param targetLanguage        a célnyelv kódja (pl. "en")
 * @param learnerTranslation    a tanuló beküldött fordítása
 * @param referenceTranslations egy vagy több elfogadható referencia-fordítás
 * @param evaluationCriteria    opcionális szöveges szempontok (a valódi providernek)
 */
public record TranslationRequest(
        String sourceText,
        String targetLanguage,
        String learnerTranslation,
        List<String> referenceTranslations,
        List<String> evaluationCriteria) {

    public TranslationRequest {
        if (learnerTranslation == null || learnerTranslation.isBlank()) {
            throw new IllegalArgumentException("learnerTranslation kötelező");
        }
        referenceTranslations = referenceTranslations == null ? List.of() : List.copyOf(referenceTranslations);
        evaluationCriteria = evaluationCriteria == null ? List.of() : List.copyOf(evaluationCriteria);
    }
}
