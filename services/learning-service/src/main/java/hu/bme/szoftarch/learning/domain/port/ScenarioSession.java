package hu.bme.szoftarch.learning.domain.port;

import java.util.UUID;

/**
 * Egy elindított párbeszédes munkamenet azonosítója és nyitó üzenete (TERV B –
 * U8). Spring-mentes domain érték.
 *
 * @param aiSessionId    az AI-oldali munkamenet azonosítója
 * @param openingMessage az AI nyitó üzenete
 */
public record ScenarioSession(UUID aiSessionId, String openingMessage) {
}
