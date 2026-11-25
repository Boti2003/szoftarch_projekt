package hu.bme.szoftarch.learning.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A felhasználó egy üzenete egy futó párbeszédes munkamenetbe (TERV B – U8,
 * {@code POST /api/v1/ai-scenarios/sessions/{sessionId}/messages}). A szöveg
 * KIZÁRÓLAG adatként jut a modellhez (soha nem utasításként).
 *
 * @param message a felhasználó üzenete (nem lehet üres)
 */
public record ScenarioMessageRequest(
        @NotBlank String message) {
}
