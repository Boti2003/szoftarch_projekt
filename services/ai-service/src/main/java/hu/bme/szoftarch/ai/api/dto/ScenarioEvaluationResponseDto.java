package hu.bme.szoftarch.ai.api.dto;

/**
 * A session záró értékelésének válasza (belső szerződés B felé). A három
 * részpontszámot és a feedbacket adja; a küszöböt és a pontszámítást B végzi,
 * ezért itt nincs overallScore vagy passed mező.
 *
 * @param comprehensibility érthetőség (0–1)
 * @param grammar           nyelvhelyesség (0–1)
 * @param taskCompletion    feladatteljesítés (0–1)
 * @param feedback          szöveges visszajelzés
 */
public record ScenarioEvaluationResponseDto(
        double comprehensibility,
        double grammar,
        double taskCompletion,
        String feedback) {
}
