package hu.bme.szoftarch.learning.infrastructure.client;

import java.util.UUID;

/**
 * A pontjóváírás kérés-body-ja az Identity belső API-nak
 * ({@code POST /internal/v1/users/{userId}/points}). Lokális DTO: a TERV D20 /
 * 3.4 szerint nincs közös domain-JAR, csak a ténylegesen használt mezőket
 * modellezzük (a {@code userId} az útvonalban megy, nem a body-ban).
 *
 * @param sourceType     a pont forrása (pl. {@code "EXERCISE"})
 * @param sourceId       a forrás azonosítója
 * @param points         a jóváírandó pont
 * @param idempotencyKey determinisztikus idempotenciakulcs
 */
public record IdentityPointsRequest(String sourceType, UUID sourceId, int points, String idempotencyKey) {
}
