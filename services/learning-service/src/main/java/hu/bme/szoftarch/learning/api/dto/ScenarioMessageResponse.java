package hu.bme.szoftarch.learning.api.dto;

/**
 * Egy párbeszédes munkamenet egy körének válasza
 * ({@code POST /api/v1/ai-scenarios/sessions/{sessionId}/messages}). A learning
 * NEM tárolja az üzenetek tartalmát (azt az ai_db birtokolja), csak a körszámot.
 *
 * <p>Ha ezzel a körrel elérte az automatikus lezárási határt, a munkamenet
 * lezárul: {@code completed=true}, és a {@code result} hordozza a záró
 * értékelést és a jóváírt pontot. Egyébként {@code completed=false} és
 * {@code result=null}.
 *
 * @param aiMessage az AI válaszüzenete
 * @param turnCount a munkamenet körszáma a válasz után
 * @param completed lezárult-e a munkamenet ezzel a körrel
 * @param result    a záró értékelés, ha lezárult; különben null
 */
public record ScenarioMessageResponse(
        String aiMessage,
        int turnCount,
        boolean completed,
        ScenarioCompleteResponse result) {
}
