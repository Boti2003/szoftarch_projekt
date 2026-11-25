package hu.bme.szoftarch.learning.infrastructure.client.dto;

/**
 * Üzenetküldés kérés-body-ja az AI belső API-nak
 * ({@code POST /internal/v1/scenario-sessions/{id}/messages}, TERV 7.7).
 * Lokális DTO: csak a felhasználói szöveg (nincs e-mail/felhasználónév/token).
 *
 * @param message a felhasználó üzenete
 */
public record ScenarioMessageRequest(String message) {
}
