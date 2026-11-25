package hu.bme.szoftarch.learning.api.dto;

/**
 * Egy párbeszédes munkamenet egy körének válasza (TERV B – U8,
 * {@code POST /api/v1/ai-scenarios/sessions/{sessionId}/messages}). A learning
 * NEM tárolja az üzenetek tartalmát (azt az ai_db birtokolja), csak a körszámot.
 *
 * @param aiMessage az AI válaszüzenete
 * @param turnCount a munkamenet körszáma a válasz után
 */
public record ScenarioMessageResponse(
        String aiMessage,
        int turnCount) {
}
