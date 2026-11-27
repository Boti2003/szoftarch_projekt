package hu.bme.szoftarch.ai.api.dto;

/**
 * Egy felhasználói forduló válasza (belső szerződés B felé). Az AI service nem
 * korlátoz és nem zár le; csak az AI válaszát és a fordulószámot adja vissza.
 *
 * @param aiMessage az AI válaszüzenete
 * @param turnCount az eddigi felhasználói fordulók száma
 */
public record ScenarioMessageResponseDto(String aiMessage, int turnCount) {
}
