package hu.bme.szoftarch.social.application.duel;

import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.common.ConcurrentUpdateException;
import hu.bme.szoftarch.social.domain.common.ForbiddenException;
import hu.bme.szoftarch.social.domain.common.InvalidStateException;
import hu.bme.szoftarch.social.domain.common.UpstreamUnavailableException;
import hu.bme.szoftarch.social.domain.duel.Duel;
import hu.bme.szoftarch.social.domain.duel.DuelEvent;
import hu.bme.szoftarch.social.domain.duel.DuelParticipant;
import hu.bme.szoftarch.social.domain.duel.DuelStateMachine;
import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import hu.bme.szoftarch.social.domain.duel.ScoreResult;
import hu.bme.szoftarch.social.domain.port.AnswerSubmission;
import hu.bme.szoftarch.social.domain.port.LearningDuelPort;
import hu.bme.szoftarch.social.infrastructure.learning.IntegrationException;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case service that orchestrates a single duel answer submission.
 *
 * <p>Implements the design "Answer orchestration" pseudocode
 * ({@code submitAnswer}). Social owns the duel state machine but delegates
 * authoritative scoring to Learning; this orchestrator loads the duel and the
 * requesting participant under optimistic locking, delegates scoring, and then
 * advances participant progress — completing the participant (and, when both
 * are done, the duel) through the single {@link DuelStateMachine} writer.
 *
 * <h2>Reliability: Learning-failure safety (requirement 5.1, design Property 10)</h2>
 * Scoring is delegated to {@link LearningDuelPort#scoreAnswer}, forwarding the
 * caller's Idempotency-Key so Learning scores at-most-once on retry
 * (requirement 5.2). The adapter exhausts its retry budget on a timeout or 5xx
 * and raises its documented failure, the Learning
 * {@link IntegrationException}. That call is the first mutating step and it runs
 * <em>before</em> any participant field is touched, so when it fails the
 * orchestrator has made no change to {@code score}, {@code answeredCount}, or
 * {@code status}. It translates the adapter failure into a domain
 * {@link UpstreamUnavailableException} (code {@code UPSTREAM_UNAVAILABLE}, mapped
 * to HTTP 502/503 by task 10.1). Catching the learning {@code IntegrationException}
 * is the cleanest available option: the port declares no typed failure and there
 * is no shared domain supertype, so importing the port's one documented failure
 * mode is the narrowest coupling possible. The identity {@code IntegrationException}
 * is deliberately <em>not</em> referenced here — the Learning path can only raise
 * the learning one.
 *
 * <h2>Scoring and completion</h2>
 * On success the award is added to the participant's running {@code score} and
 * {@code answeredCount} is incremented; when the participant reaches the fixed
 * {@link #EXERCISE_COUNT} answers the {@link DuelEvent#COMPLETE_PARTICIPANT}
 * event is driven through the state machine, which completes the duel and sets
 * the winner exactly once when both participants are COMPLETED. The duel awards
 * competition points only — <em>no</em> Identity profile-point mutation is
 * performed (requirement 3.7, design Property 12); the orchestrator holds no
 * Identity write port and makes no such call.
 *
 * <h2>Persistence conventions (mirrors {@link DuelService})</h2>
 * The persisted entity rows are the source of truth; the mutated state is
 * written back and saved within the transaction, and an
 * {@link OptimisticLockingFailureException} from the participant {@code @Version}
 * check is mapped to {@link ConcurrentUpdateException} (HTTP 409
 * {@code CONCURRENT_UPDATE}). Tokens, keys, and correct answers are never logged
 * (requirement 13.2).
 */
@Service
public class DuelAnswerOrchestrator {

    /** Fixed number of auto-gradable exercises per duel; completion threshold. */
    static final int EXERCISE_COUNT = 5;

    private final DuelRepository duelRepository;
    private final DuelParticipantRepository participantRepository;
    private final LearningDuelPort learningDuelPort;
    private final DuelStateMachine stateMachine;
    private final Clock clock;

    public DuelAnswerOrchestrator(DuelRepository duelRepository,
                                  DuelParticipantRepository participantRepository,
                                  LearningDuelPort learningDuelPort,
                                  DuelStateMachine stateMachine,
                                  Clock clock) {
        this.duelRepository = Objects.requireNonNull(duelRepository);
        this.participantRepository = Objects.requireNonNull(participantRepository);
        this.learningDuelPort = Objects.requireNonNull(learningDuelPort);
        this.stateMachine = Objects.requireNonNull(stateMachine);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Scores a single answer for the acting participant and advances their
     * progress, completing the participant and duel when the fixed exercise
     * count is reached.
     *
     * @param userId         the acting user id (JWT {@code sub}); must be a
     *                       participant of the duel
     * @param duelId         the duel the answer belongs to
     * @param submission     the participant's answer, forwarded to Learning for
     *                       authoritative scoring
     * @param idempotencyKey the caller's Idempotency-Key, forwarded to Learning
     *                       so scoring is at-most-once on retry (requirement 5.2)
     * @return the answer outcome and the acting participant's own progress
     * @throws ForbiddenException            if the user is not a participant of
     *                                       the duel (403; also raised when the
     *                                       duel does not exist, to avoid leaking
     *                                       existence)
     * @throws InvalidStateException         if the duel or the participant is not
     *                                       IN_PROGRESS (409)
     * @throws UpstreamUnavailableException  if Learning scoring fails after its
     *                                       retry budget; no state is mutated
     *                                       (502/503)
     * @throws ConcurrentUpdateException     if a concurrent write loses the
     *                                       optimistic-lock check (409)
     */
    @Transactional
    public AnswerResultView submitAnswer(UUID userId, UUID duelId,
                                         AnswerSubmission submission, UUID idempotencyKey) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(duelId, "duelId must not be null");
        Objects.requireNonNull(submission, "submission must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");

        // 1. Load the duel and both participant rows (participant @Version loaded).
        //    A missing duel or a non-participant is reported as 403 so existence
        //    of duels the caller is not part of is never disclosed.
        DuelEntity duelEntity = duelRepository.findById(duelId)
                .orElseThrow(() -> new ForbiddenException("not a participant of this duel"));
        if (!userId.equals(duelEntity.getChallengerId()) && !userId.equals(duelEntity.getOpponentId())) {
            throw new ForbiddenException("not a participant of this duel");
        }
        DuelParticipantEntity challengerEntity = participant(duelId, duelEntity.getChallengerId());
        DuelParticipantEntity opponentEntity = participant(duelId, duelEntity.getOpponentId());
        DuelParticipantEntity self = userId.equals(duelEntity.getChallengerId())
                ? challengerEntity : opponentEntity;

        // 2. Lazy expiry, then require both the duel and the participant IN_PROGRESS.
        Duel duel = toDomain(duelEntity, challengerEntity, opponentEntity);
        stateMachine.applyExpiry(duel);
        if (duel.status() != DuelStatus.IN_PROGRESS
                || self.getStatus() != ParticipantStatus.IN_PROGRESS) {
            throw new InvalidStateException(
                    "cannot answer: duel and participant must both be IN_PROGRESS");
        }

        // 3. Delegate authoritative scoring to Learning (idempotent via the key).
        //    This runs before any participant field is touched, so a Learning
        //    failure leaves score/answeredCount/status unchanged (Property 10).
        ScoreResult score = scoreViaLearning(duelEntity.getLearningSetId(), submission, idempotencyKey);

        // 4. Apply the award and advance progress on the entity (the source of
        //    truth), then drive COMPLETE_PARTICIPANT through the state machine at
        //    the fifth answer so it may complete the duel and set the winner.
        self.setScore(self.getScore() + score.awardedPoints());
        self.setAnsweredCount(self.getAnsweredCount() + 1);

        Duel advanced = toDomain(duelEntity, challengerEntity, opponentEntity);
        if (self.getAnsweredCount() == EXERCISE_COUNT) {
            stateMachine.apply(advanced, DuelEvent.COMPLETE_PARTICIPANT, userId);
        }

        // 5. Persist within the transaction; map a lost @Version check to 409.
        persist(advanced, duelEntity, challengerEntity, opponentEntity);

        // 6. Produce competition points only — no Identity profile-point call is
        //    made here (no Identity write port is injected). Report the acting
        //    participant's own progress only, never the opponent's.
        DuelParticipant progressed = advanced.participantOf(userId);
        UUID winnerId = advanced.status() == DuelStatus.COMPLETED ? advanced.winnerId() : null;
        return new AnswerResultView(
                duelId,
                score.correct(),
                score.awardedPoints(),
                progressed.score(),
                progressed.answeredCount(),
                progressed.status(),
                advanced.status(),
                winnerId);
    }

    // --- internals ---------------------------------------------------------

    /**
     * Delegates scoring to Learning, forwarding the Idempotency-Key, and
     * translates the adapter's integration failure into the domain
     * {@link UpstreamUnavailableException}. No participant state is mutated
     * before or during this call, so a failure here advances nothing.
     */
    private ScoreResult scoreViaLearning(UUID learningSetId, AnswerSubmission submission, UUID idempotencyKey) {
        try {
            return learningDuelPort.scoreAnswer(learningSetId, submission, idempotencyKey);
        } catch (IntegrationException upstream) {
            // Reliability rule: do NOT advance participant state on Learning error.
            throw new UpstreamUnavailableException(
                    "scoring is temporarily unavailable; retry with the same Idempotency-Key", upstream);
        }
    }

    private DuelParticipantEntity participant(UUID duelId, UUID userId) {
        return participantRepository.findByDuelIdAndUserId(duelId, userId)
                .orElseThrow(() -> new IllegalStateException(
                        "duel " + duelId + " is missing participant row for " + userId));
    }

    /** Reconstitutes the pure-domain aggregate from the persisted rows. */
    private static Duel toDomain(DuelEntity duel,
                                 DuelParticipantEntity challenger,
                                 DuelParticipantEntity opponent) {
        return new Duel(
                duel.getId(), duel.getChallengerId(), duel.getOpponentId(),
                duel.getStatus(), duel.getExpiresAt(), duel.getWinnerId(),
                toParticipant(challenger), toParticipant(opponent));
    }

    private static DuelParticipant toParticipant(DuelParticipantEntity e) {
        return new DuelParticipant(
                e.getUserId(), e.getStatus(), e.getScore(), e.getAnsweredCount(),
                e.getStartedAt(), e.getCompletedAt());
    }

    /**
     * Writes the mutated domain state back to the JPA entities and saves them
     * within the transaction. The participant {@code @Version} guards against a
     * concurrent write; a lost check is surfaced as
     * {@link ConcurrentUpdateException} (HTTP 409), matching {@link DuelService}.
     */
    private void persist(Duel duel, DuelEntity duelEntity,
                         DuelParticipantEntity challengerEntity,
                         DuelParticipantEntity opponentEntity) {
        Instant now = clock.now();

        duelEntity.setStatus(duel.status());
        duelEntity.setExpiresAt(duel.expiresAt());
        duelEntity.setWinnerId(duel.winnerId());
        duelEntity.setUpdatedAt(now);

        copyParticipantState(duel, duelEntity.getChallengerId(), challengerEntity);
        copyParticipantState(duel, duelEntity.getOpponentId(), opponentEntity);

        try {
            duelRepository.save(duelEntity);
            participantRepository.save(challengerEntity);
            participantRepository.save(opponentEntity);
        } catch (OptimisticLockingFailureException conflict) {
            throw new ConcurrentUpdateException("duel was updated concurrently; retry");
        }
    }

    private static void copyParticipantState(Duel duel, UUID userId, DuelParticipantEntity entity) {
        DuelParticipant p = duel.participantOf(userId);
        entity.setStatus(p.status());
        entity.setScore(p.score());
        entity.setAnsweredCount(p.answeredCount());
        entity.setStartedAt(p.startedAt());
        entity.setCompletedAt(p.completedAt());
    }
}
