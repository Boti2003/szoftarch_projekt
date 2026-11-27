package hu.bme.szoftarch.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Session indítás kérés. A hívó service (Learning) küldi a megbízható
 * kontextust az exercise payloadból: öt kötelező config-mező. A hívó nem
 * ad át belső azonosítót; a session saját UUID-t kap.
 */
public record StartScenarioRequestDto(
        @NotBlank String role,
        @NotBlank String situation,
        @NotEmpty List<@NotBlank String> goals,
        @NotBlank String targetLanguageCode,
        @NotBlank String openingMessage) {
}
