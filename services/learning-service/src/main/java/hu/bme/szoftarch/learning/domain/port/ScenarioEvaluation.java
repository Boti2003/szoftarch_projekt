package hu.bme.szoftarch.learning.domain.port;

import java.math.BigDecimal;

/**
 * Egy lezárt párbeszédes munkamenet végső kiértékelése (TERV B – U8). Spring-
 * mentes domain érték; a három részpont az {@code [0, 1]} intervallumban van.
 *
 * <p><strong>Kritikus:</strong> a {@code feedback} SOHA nem tartalmazhatja a
 * helyes megoldást (TERV 5.2).</p>
 *
 * @param comprehensibility az érthetőség részpontja
 * @param grammar           a nyelvtan részpontja
 * @param taskCompletion    a feladatteljesítés részpontja
 * @param feedback          rövid, megoldást nem szivárogtató visszajelzés
 */
public record ScenarioEvaluation(BigDecimal comprehensibility, BigDecimal grammar,
        BigDecimal taskCompletion, String feedback) {
}
