package hu.bme.szoftarch.learning.api.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Párbaj-feladatkészlet létrehozás kérésteste a belső
 * {@code POST /internal/v1/duel-sets} végponthoz (TERV 5.5, 7.5). A Social
 * service hívja; a résztvevő userId-kat a Social kezeli, a Learning csak a
 * készletet gyártja.
 *
 * @param duelId     a Social párbaj UUID-ja; erre EGYEDI (idempotencia +
 *     verseny-kezelés a {@code duel_sets.external_duel_id} unique megszorítással)
 * @param languageId a párbaj nyelve (a kihívó választja)
 * @param expiresAt  opcionális lejárat; ha a Social átadja, a Learning tárolja
 */
public record CreateDuelSetRequest(
        @NotNull UUID duelId,
        @NotNull UUID languageId,
        Instant expiresAt) {
}
