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
 * timeouts, at-most-one retry, Idempotency-Key forwarding) and raises an
 * integration error after the retry budget; on such an error the caller must
 * leave participant state unchanged (design "Learning failure safety").
 *
 * <p>This is a pure domain contract with no Spring or JPA dependency. It
 * reuses {@link ScoreResult} from {@code domain.duel} for scoring outcomes.
 */
public interface LearningDuelPort {

    /**
     * Requests Learning to build a duel exercise set for the given language.
     *
     * @param languageId    the language the duel is played in; must not be
     *                      {@code null}
     * @param exerciseCount the number of auto-gradable exercises to generate
     * @return a reference to the created duel set; never {@code null}
     */
    DuelSetRef createDuelSet(UUID languageId, int exerciseCount);

    /**
     * Fetches the exercises of a duel set, in a stable order shared by both
     * participants, without correct solutions.
     *
     * @param setId the duel-set id returned by {@link #createDuelSet}; must not
     *              be {@code null}
     * @return the exercises in presentation order; never {@code null}
     */
    List<ExerciseRef> fetchExercises(UUID setId);

    /**
     * Delegates authoritative scoring of a single answer to Learning.
     *
     * <p>The call is idempotent with respect to {@code idemKey}: retrying with
     * the same key does not double-score.
     *
     * @param setId      the duel-set id the answer belongs to; must not be
     *                   {@code null}
     * @param submission the participant's answer; must not be {@code null}
     * @param idemKey    the caller's Idempotency-Key, forwarded to Learning;
     *                   must not be {@code null}
     * @return the scoring outcome; never {@code null}
     */
    ScoreResult scoreAnswer(UUID setId, AnswerSubmission submission, UUID idemKey);
}
