package hu.bme.szoftarch.learning.api.dto;

/**
 * Párbaj-válasz pontozás válasza a belső
 * {@code POST /internal/v1/duel-sets/{setId}/answers} végponthoz (TERV 5.5,
 * 7.5). Kizárólag az ADOTT válasz eredményét hordozza.
 *
 * <p><strong>Kritikus (TERV 5.5):</strong> a válasz NEM szivárogtatja a többi
 * résztvevő eredményét vagy a helyes megoldást — csak a beküldött válasz
 * pozícióját, pontszámát és helyességét.</p>
 *
 * @param position a beküldött válasz pozíciója
 * @param score    a párbajpont: helyes válaszra az exercise max_points értéke,
 *     egyébként 0 (NEM profilpont)
 * @param correct  igaz, ha a válasz teljesen helyes
 */
public record DuelAnswerResponse(
        int position,
        int score,
        boolean correct) {
}
