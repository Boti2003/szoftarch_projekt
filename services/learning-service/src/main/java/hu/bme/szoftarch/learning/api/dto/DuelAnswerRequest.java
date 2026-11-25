package hu.bme.szoftarch.learning.api.dto;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

/**
 * Párbaj-válasz beküldés kérésteste a belső
 * {@code POST /internal/v1/duel-sets/{setId}/answers} végponthoz (TERV 5.5,
 * 7.5). A {@code userId} a Socialtól érkezik a payloadban (NEM JWT-ből — ez
 * belső service-to-service hívás user-kontextus nélkül).
 *
 * @param userId   a résztvevő azonosítója (a Social adja át)
 * @param position a feladat pozíciója a mentett készletben
 * @param answer   a feladattípushoz tartozó nyers válasz JSON; a típusos
 *     {@code UserAnswer} recordra oldását az {@code ExerciseContentCodec} végzi
 */
public record DuelAnswerRequest(
        @NotNull UUID userId,
        int position,
        @NotNull JsonNode answer) {
}
