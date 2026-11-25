package hu.bme.szoftarch.learning.api.dto;

import java.util.UUID;

/**
 * Egy párbeszédes ({@code AI_SCENARIO}) munkamenet indításának válasza
 * (TERV B – U8, {@code POST /api/v1/ai-scenarios/{exerciseId}/sessions}).
 *
 * @param sessionId      az AI-oldali munkamenet azonosítója (ezzel megy a
 *     további üzenetváltás és a lezárás)
 * @param attemptId      a létrejött (vagy idempotensen visszaadott) LESSON
 *     kontextusú beküldés azonosítója
 * @param openingMessage az AI nyitó üzenete
 */
public record StartScenarioSessionResponse(
        UUID sessionId,
        UUID attemptId,
        String openingMessage) {
}
