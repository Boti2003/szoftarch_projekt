package hu.bme.szoftarch.ai.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Session indítás kérés. A hívó service küldi a megbízható kontextust.
 */
public record StartScenarioRequestDto(
        @NotNull UUID learningAttemptId,
        @NotNull UUID userId,
        @NotNull UUID exerciseId,
        String role,
        String situation,
        String goal,
        String targetLanguage,
        String openingMessage) {
}
