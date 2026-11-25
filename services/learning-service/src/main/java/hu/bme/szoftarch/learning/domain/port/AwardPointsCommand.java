package hu.bme.szoftarch.learning.domain.port;

import java.util.UUID;

/**
 * A pontjóváírás kimenő parancsa (TERV B – U5, Adapter minta bemenete). Spring-
 * és HTTP-mentes domain érték; az infrastruktúra adaptere fordítja REST-hívásra.
 *
 * @param userId         a jóváírás kedvezményezettje (Identity-beli UUID)
 * @param sourceType     a pont forrása (pl. {@code "EXERCISE"})
 * @param sourceId       a forrás azonosítója (pl. a feladat UUID-ja)
 * @param points         a jóváírandó pont
 * @param idempotencyKey determinisztikus kulcs; azonos kulcs nem ad dupla pontot
 */
public record AwardPointsCommand(UUID userId, String sourceType, UUID sourceId, int points,
        String idempotencyKey) {
}
