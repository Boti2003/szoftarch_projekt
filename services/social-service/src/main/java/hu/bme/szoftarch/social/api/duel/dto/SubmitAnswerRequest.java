package hu.bme.szoftarch.social.api.duel.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/duels/{id}/answers} (requirements 3.11,
 * 5.1).
 *
 * <p>Carries the exercise being answered and the submitted answer value (a
 * selected option or free text). The controller maps this to a domain
 * {@code AnswerSubmission} and forwards it, with the acting user id (JWT
 * {@code sub}) and the {@code Idempotency-Key}, to the duel answer orchestrator;
 * Learning owns authoritative scoring. Both fields are mandatory; a missing
 * value is rejected as a 400 {@code VALIDATION_FAILED} before the use case runs.
 *
 * @param exerciseId the exercise being answered
 * @param answer     the submitted answer value (selected option or free text)
 */
public record SubmitAnswerRequest(
        @NotNull UUID exerciseId,
        @NotNull String answer) {
}
