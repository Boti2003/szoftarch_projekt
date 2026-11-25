package hu.bme.szoftarch.learning.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;

/**
 * Egy feladat-beküldés kiértékelt eredménye a
 * {@code POST /api/v1/exercises/{exerciseId}/attempts} válaszában (TERV 7.4).
 *
 * <p><strong>Kritikus (TERV 5.2):</strong> a válasz KIZÁRÓLAG a beküldés
 * eredményét adja vissza — a helyes megoldás egyetlen mezőben sem jelenik meg,
 * és a {@code feedback} sem szivárogtatja azt.</p>
 *
 * @param attemptId        a létrejött (vagy idempotensen visszaadott) beküldés azonosítója
 * @param scoreRatio       az elért pontarány a {@code [0, 1]} intervallumban
 * @param correct          igaz, ha a válasz teljesen helyes
 * @param feedback         megoldást nem szivárogtató visszajelzés
 * @param pointAwardStatus a pontjóváírás szándékának állapota (U5 dolgozza fel)
 */
public record AttemptResponse(
        UUID attemptId,
        BigDecimal scoreRatio,
        boolean correct,
        String feedback,
        PointAwardStatus pointAwardStatus) {
}
