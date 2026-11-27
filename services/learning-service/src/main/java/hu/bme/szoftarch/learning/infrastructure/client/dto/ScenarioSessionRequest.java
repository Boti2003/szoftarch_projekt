package hu.bme.szoftarch.learning.infrastructure.client.dto;

import java.util.List;

/**
 * Párbeszédes munkamenet indításának kérés-body-ja az AI belső API-nak
 * ({@code POST /internal/v1/scenario-sessions}, TERV 7.7). Lokális DTO: csak
 * megbízható feladat-konfiguráció (nincs e-mail/felhasználónév/token).
 *
 * @param role               az AI szerepe
 * @param situation          a szituáció leírása
 * @param goals              a teljesítendő célok
 * @param targetLanguageCode a célnyelv kódja
 * @param openingMessage     a nyitó üzenet
 */
public record ScenarioSessionRequest(String role, String situation, List<String> goals,
        String targetLanguageCode, String openingMessage) {
}
