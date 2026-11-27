package hu.bme.szoftarch.learning.domain.port;

import java.util.UUID;

/**
 * Párbeszédes munkamenet lezárásának kimenő parancsa (TERV B – U8, Adapter
 * minta bemenete). Spring- és HTTP-mentes domain érték.
 *
 * @param aiSessionId az AI-oldali munkamenet azonosítója
 */
public record CompleteScenarioCommand(UUID aiSessionId) {
}
