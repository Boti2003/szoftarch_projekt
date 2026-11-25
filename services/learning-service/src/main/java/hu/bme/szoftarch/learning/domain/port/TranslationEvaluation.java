package hu.bme.szoftarch.learning.domain.port;

import java.math.BigDecimal;

/**
 * A fordítós AI-kiértékelés eredménye (TERV B – U8). Spring-mentes domain érték.
 *
 * <p><strong>Kritikus:</strong> a {@code feedback} SOHA nem tartalmazhatja a
 * helyes megoldást (TERV 5.2).</p>
 *
 * @param score    a pontarány a {@code [0, 1]} intervallumban
 * @param accepted igaz, ha a fordítás elfogadott (a küszöb felett van)
 * @param feedback rövid, megoldást nem szivárogtató visszajelzés
 */
public record TranslationEvaluation(BigDecimal score, boolean accepted, String feedback) {
}
