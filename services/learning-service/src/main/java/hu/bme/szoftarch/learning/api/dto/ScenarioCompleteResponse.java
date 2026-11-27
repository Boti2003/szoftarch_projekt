package hu.bme.szoftarch.learning.api.dto;

import java.math.BigDecimal;

/**
 * Egy párbeszédes munkamenet lezárásának kiértékelt eredménye (TERV B – U8,
 * {@code POST /api/v1/ai-scenarios/sessions/{sessionId}/complete}).
 *
 * <p><strong>Kritikus (TERV 5.2):</strong> a válasz KIZÁRÓLAG az eredményt adja
 * vissza — a helyes megoldás egyetlen mezőben sem jelenik meg, a
 * {@code feedback} sem szivárogtatja azt; a pont szerveroldalon dől el.</p>
 *
 * @param scoreRatio a három részpont átlaga a {@code [0, 1]} intervallumban
 * @param completed  igaz, ha az átlag eléri a 0.60 teljesítési küszöböt
 * @param points     a szerveroldalon kiértékelt, jóváírandó pont (0, ha nem teljesült)
 * @param feedback   megoldást nem szivárogtató visszajelzés
 */
public record ScenarioCompleteResponse(
        BigDecimal scoreRatio,
        boolean completed,
        int points,
        String feedback) {
}
