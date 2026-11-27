package hu.bme.szoftarch.learning.infrastructure.client.dto;

/**
 * Üzenetküldés válasz-body-ja az AI belső API-tól. Lokális DTO.
 *
 * @param aiMessage az AI válaszüzenete
 * @param turnCount a munkamenet körszáma
 */
public record ScenarioMessageResponse(String aiMessage, int turnCount) {
}
