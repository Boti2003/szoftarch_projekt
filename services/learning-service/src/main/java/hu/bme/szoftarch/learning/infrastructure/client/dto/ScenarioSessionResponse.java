package hu.bme.szoftarch.learning.infrastructure.client.dto;

import java.util.UUID;

/**
 * Párbeszédes munkamenet indításának válasz-body-ja az AI belső API-tól
 * (TERV 7.7). Lokális DTO.
 *
 * @param id             az AI-oldali munkamenet azonosítója
 * @param openingMessage az AI nyitó üzenete
 */
public record ScenarioSessionResponse(UUID id, String openingMessage) {
}
