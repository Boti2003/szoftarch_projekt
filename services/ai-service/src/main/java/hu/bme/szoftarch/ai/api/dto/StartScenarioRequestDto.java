package hu.bme.szoftarch.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Session indítás kérés. A hívó service (Learning) küldi a megbízható
 * kontextust az exercise payloadból. Mind az öt config-mező kötelező.
 */
public record StartScenarioRequestDto(
        @NotNull UUID learningAttemptId,
        @NotNull UUID userId,
        @NotNull UUID exerciseId,
        @NotBlank String role,
        @NotBlank String situation,
        @NotEmpty List<@NotBlank String> goals,
        @NotBlank String targetLanguageCode,
        @NotBlank String openingMessage) {
}
