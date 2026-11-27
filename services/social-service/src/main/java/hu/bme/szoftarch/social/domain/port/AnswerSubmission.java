package hu.bme.szoftarch.social.domain.port;

import java.util.UUID;

/**
 * A participant's answer to a single duel exercise, forwarded to the Learning
 * service for authoritative scoring.
 *
 * <p>Used by {@link LearningDuelPort#scoreAnswer}. The {@code answer} carries
 * the submitted value (a selected option or free text); Learning owns the
 * grading and returns a score.
 *
 * @param exerciseId the exercise being answered
 * @param answer     the submitted answer value
 */
public record AnswerSubmission(UUID exerciseId, String answer) {
}
