package hu.bme.szoftarch.ai.api.dto;

/**
 * Egy felhasználói forduló válasza (belső szerződés B felé).
 *
 * @param aiMessage az AI válaszüzenete
 * @param turnCount az eddigi felhasználói fordulók száma
 */
public record ScenarioMessageResponseDto(String aiMessage, int turnCount) {
}
