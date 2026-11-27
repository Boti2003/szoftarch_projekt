package hu.bme.szoftarch.learning.domain.port;

import java.util.List;

/**
 * Párbeszédes munkamenet indításának kimenő parancsa (TERV B – U8, Adapter
 * minta bemenete). Spring- és HTTP-mentes domain érték. KIZÁRÓLAG megbízható
 * feladat-konfigurációt hordoz (nincs e-mail/felhasználónév/token).
 *
 * @param role               az AI által megszemélyesített szerep
 * @param situation          a párbeszéd szituációja
 * @param goals              a felhasználó teljesítendő céljai
 * @param targetLanguageCode a célnyelv kódja (pl. {@code en})
 * @param openingMessage     az AI nyitó üzenete
 */
public record StartScenarioCommand(String role, String situation, List<String> goals,
        String targetLanguageCode, String openingMessage) {
}
