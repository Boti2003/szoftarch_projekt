package hu.bme.szoftarch.learning.infrastructure.client.dto;

import java.math.BigDecimal;

/**
 * Fordítós kiértékelés válasz-body-ja az AI belső API-tól (TERV 7.7). Lokális
 * DTO.
 *
 * @param score    a pontarány a {@code [0, 1]} intervallumban
 * @param accepted igaz, ha a fordítás elfogadott
 * @param feedback megoldást nem szivárogtató visszajelzés
 */
public record TranslationEvaluateResponse(BigDecimal score, boolean accepted, String feedback) {
}
