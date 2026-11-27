package hu.bme.szoftarch.social.api.duel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

/**
 * Request body for {@code POST /api/v1/duels/{id}/answers}.
 *
 * <p>Learning scores duel answers by {@code position} (the exercise's index in
 * the duel set) and expects a type-specific answer object (e.g.
 * {@code {"selectedOptionIds":["a"]}} for multiple choice). The client sends the
 * {@code position} of the exercise being answered and the type-specific
 * {@code answer} payload; the controller maps these to a domain
 * {@code AnswerSubmission} and forwards them, with the acting user id (JWT
 * {@code sub}), to the orchestrator. A missing field is rejected as a 400 before
 * the use case runs.
 *
 * @param position the 0-based exercise position being answered
 * @param answer   the type-specific answer payload (a JSON object)
 */
public record SubmitAnswerRequest(
        @NotNull @PositiveOrZero Integer position,
        @NotNull Map<String, Object> answer) {
}
