package hu.bme.szoftarch.ai.api.dto;

import java.util.UUID;

/**
 * A session indításának válasza (belső szerződés B felé).
 *
 * @param id             a létrejött session azonosítója
 * @param openingMessage az AI nyitó üzenete
 */
public record StartScenarioResponseDto(UUID id, String openingMessage) {
}
