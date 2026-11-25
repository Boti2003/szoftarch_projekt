package hu.bme.szoftarch.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Egy felhasználói forduló szövege.
 */
public record ScenarioMessageRequestDto(
        @NotBlank(message = "A message nem lehet üres") String message) {
}
