package hu.bme.szoftarch.learning.infrastructure.client.dto;

import java.math.BigDecimal;

/**
 * Párbeszédes munkamenet lezárásának válasz-body-ja az AI belső API-tól
 * ({@code POST /internal/v1/scenario-sessions/{id}/complete}, TERV 7.7).
 * Lokális DTO.
 *
 * @param comprehensibility az érthetőség részpontja
 * @param grammar           a nyelvtan részpontja
 * @param taskCompletion    a feladatteljesítés részpontja
 * @param feedback          megoldást nem szivárogtató visszajelzés
 */
public record ScenarioCompleteResponse(BigDecimal comprehensibility, BigDecimal grammar,
        BigDecimal taskCompletion, String feedback) {
}
