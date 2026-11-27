package hu.bme.szoftarch.social.domain.port;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.social.domain.duel.ScoreResult;

/**
 * Outbound port used to orchestrate duel exercise sets and authoritative
 * answer scoring through the Learning service.
 *
 * <p>Implemented by an infrastructure adapter backed by Learning's internal
 * API ({@code /internal/v1/duel-sets} and related endpoints). Social owns the
 * duel state machine but delegates exercise generation and answer scoring to
 * Learning. The adapter applies integration concerns (internal headers,
 * timeouts, at-most-one retry) and raises an integration error after the retry
 * budget; on such an error the caller must leave participant state unchanged
 * (design "Learning failure safety").
 *
 * <p>Learning keys a duel set by Social's own {@code duelId} and scores answers
 * by {@code position} + participant {@code userId}, so the port is modelled
 * accordingly. This is a pure domain contract with no Spring or JPA dependency.
 */
public interface LearningDuelPort {

    /**
     * Requests Learning to build (or idempotently return) the duel exercise set
     * for Social's {@code duelId}, in the given language. Learning returns the
     * set id together with the ordered exercise presentations in one call.
     *
     * @param duelId     Social's duel id (Learning keys the set on it); must not
     *                   be {@code null}
     * @param languageId the language the duel is played in; must not be {@code null}
     * @return the created set reference with its ordered exercises; never {@code null}
     */
    DuelSetRef createDuelSet(UUID duelId, UUID languageId);

    /**
     * Fetches the exercises of a duel set, in position order shared by both
     * participants, without correct solutions.
     *
     * @param setId the duel-set id; must not be {@code null}
     * @return the exercises in position order; never {@code null}
     */
    List<ExerciseRef> fetchExercises(UUID setId);

    /**
     * Delegates authoritative scoring of a single answer to Learning.
     *
     * <p>Learning is idempotent on {@code (setId, userId, position)}: resubmitting
     * the same position for the same user returns the original score without
     * re-scoring.
     *
     * @param setId      the duel-set id the answer belongs to; must not be {@code null}
     * @param userId     the answering participant's id (Social supplies it; this
     *                   is a service-to-service call with no JWT context)
     * @param submission the participant's answer (position + payload); must not
     *                   be {@code null}
     * @param idemKey    the caller's Idempotency-Key (forwarded as a header for
     *                   traceability); must not be {@code null}
     * @return the scoring outcome; never {@code null}
     */
    ScoreResult scoreAnswer(UUID setId, UUID userId, AnswerSubmission submission, UUID idemKey);
}
