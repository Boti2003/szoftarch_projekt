package hu.bme.szoftarch.learning.api.dto;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

/**
 * Feladat-beküldés kérésteste a
 * {@code POST /api/v1/exercises/{exerciseId}/attempts} végponthoz (TERV 7.4).
 * Csak a nyers felhasználói választ hordozza; a {@code userId} SOHA nem innen,
 * hanem a JWT {@code sub} claimjéből származik (TERV 7.).
 *
 * @param answer a feladattípushoz tartozó nyers válasz JSON; a típusos
 *     {@code UserAnswer} recordra oldását az {@code ExerciseContentCodec} végzi
 */
public record AttemptRequest(@NotNull JsonNode answer) {
}
