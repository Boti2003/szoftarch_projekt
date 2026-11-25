package hu.bme.szoftarch.learning.infrastructure.client.dto;

/**
 * Üzenetküldés válasz-body-ja az AI belső API-tól (TERV 7.7). Lokális DTO.
 *
 * @param message   az AI válaszüzenete
 * @param turnCount a munkamenet körszáma
 */
public record ScenarioMessageResponse(String message, int turnCount) {
}
