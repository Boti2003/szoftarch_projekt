package hu.bme.szoftarch.social.application.duel;

import hu.bme.szoftarch.social.application.common.PageQuery;
import hu.bme.szoftarch.social.application.common.PageResult;
import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.common.ConcurrentUpdateException;
import hu.bme.szoftarch.social.domain.common.ForbiddenException;
import hu.bme.szoftarch.social.domain.duel.Duel;
import hu.bme.szoftarch.social.domain.duel.DuelEvent;
import hu.bme.szoftarch.social.domain.duel.DuelParticipant;
import hu.bme.szoftarch.social.domain.duel.DuelStateMachine;
import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import hu.bme.szoftarch.social.domain.friend.FriendPair;
import hu.bme.szoftarch.social.domain.port.DuelSetRef;
import hu.bme.szoftarch.social.domain.port.ExerciseRef;
import hu.bme.szoftarch.social.domain.port.LearningDuelPort;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case service for the duel lifecycle: creation between accepted friends,
 * accept/reject by the opponent, paged listing, gated detail reads, and serving
 * the shared exercise set.
 *
 * <p>The acting user id is always the {@code sub}-sourced id passed in by the
 * API layer (requirement 4.1); it is never read from a DTO field. All duel
 * status, expiry, and winner changes are driven through the single
 * {@link DuelStateMachine} writer: the service loads the persisted duel and its
 * two participants into a pure-domain {@link Duel} aggregate, applies the state
 * machine, then writes the mutated aggregate back to the JPA entities within
 * the transaction.
 *
 * <h2>Friends-only creation</h2>
 * A duel may only be created between two distinct users who are accepted
 * friends (requirement 3.1). The check normalizes the pair via
 * {@link FriendPair#of} and asks {@link FriendshipRepository} whether the
 * ordered-pair friendship exists. There is no dedicated "not friends" error
 * code in the design error table, so a non-friend (or self) creation is
 * rejected with {@link ForbiddenException} (HTTP 403 {@code FORBIDDEN}); this
 * mirrors the service's treatment of other authorization failures and avoids
 * leaking relationship state. (A later friends-only property test, task 8.3
 * Property 4, asserts this creation guard.)
 *
 * <h2>Result-privacy gating</h2>
 * {@link #get} withholds the opponent's detailed result until the requesting
 * participant's own status is {@link ParticipantStatus#COMPLETED}
 * (requirement 3.6 / design Property 9), enforced server-side.
 */
@Service
public class DuelService {

    /** Fixed number of auto-gradable exercises per duel set (requirement 3.1). */
    static final int EXERCISE_COUNT = 5;

    /** PENDING duels expire 72 hours after creation (requirement 3.3). */
    static final Duration PENDING_WINDOW = DuelStateMachine.PENDING_WINDOW;

    private final DuelRepository duelRepository;
    private final DuelParticipantRepository participantRepository;
    private final FriendshipRepository friendshipRepository;
    private final LearningDuelPort learningDuelPort;
    private final DuelStateMachine stateMachine;
    private final Clock clock;

    public DuelService(DuelRepository duelRepository,
                       DuelParticipantRepository participantRepository,
                       FriendshipRepository friendshipRepository,
                       LearningDuelPort learningDuelPort,
                       DuelStateMachine stateMachine,
                       Clock clock) {
        this.duelRepository = Objects.requireNonNull(duelRepository);
        this.participantRepository = Objects.requireNonNull(participantRepository);
        this.friendshipRepository = Objects.requireNonNull(friendshipRepository);
        this.learningDuelPort = Objects.requireNonNull(learningDuelPort);
        this.stateMachine = Objects.requireNonNull(stateMachine);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Creates a PENDING duel between the challenger and an accepted-friend
     * opponent, records the challenger-selected language, asks Learning to build
     * the fixed five-exercise set, and persists the {@code learning_set_id}
     * (requirement 3.1).
     *
     * @param challengerId the acting challenger id (JWT {@code sub})
     * @param opponentId   the opponent to challenge
     * @param languageId   the challenger-selected language
     * @return a {@link DuelView} of the created PENDING duel
     * @throws ForbiddenException if challenger and opponent are the same user or
     *                            they are not accepted friends (403)
     */
    @Transactional
    public DuelView create(UUID challengerId, UUID opponentId, UUID languageId) {
        Objects.requireNonNull(challengerId, "challengerId must not be null");
        Objects.requireNonNull(opponentId, "opponentId must not be null");
        Objects.requireNonNull(languageId, "languageId must not be null");

        if (challengerId.equals(opponentId)) {
            throw new ForbiddenException("cannot create a duel with yourself");
        }
        // Friends-only: the normalized ordered pair must have a stored friendship.
        FriendPair pair = FriendPair.of(challengerId, opponentId);
        if (!friendshipRepository.existsByUserLowIdAndUserHighId(pair.low(), pair.high())) {
            throw new ForbiddenException("can only duel an accepted friend");
        }

        // Learning keys the duel set on Social's duelId, so generate it first.
        UUID duelId = UUID.randomUUID();

        // Delegate fixed exercise-set generation to Learning before persisting.
        DuelSetRef set = learningDuelPort.createDuelSet(duelId, languageId);

        Instant now = clock.now();
        Instant expiresAt = now.plus(PENDING_WINDOW);

        DuelEntity duel = new DuelEntity(
                duelId, challengerId, opponentId, languageId,
                set.setId(), DuelStatus.PENDING, expiresAt, null, now, now);
        duelRepository.save(duel);

        participantRepository.save(new DuelParticipantEntity(
                duelId, challengerId, ParticipantStatus.NOT_STARTED, 0, 0, null, null));
        participantRepository.save(new DuelParticipantEntity(
                duelId, opponentId, ParticipantStatus.NOT_STARTED, 0, 0, null, null));

        return new DuelView(duelId, challengerId, opponentId, languageId,
                set.setId(), DuelStatus.PENDING, expiresAt);
    }

    /**
     * Accepts a PENDING duel. Only the opponent may accept; acceptance moves the
     * duel to ACCEPTED and extends its expiry to 7 days (requirements 3.8, 3.10).
     *
     * @param userId the acting user id (JWT {@code sub}); must be the opponent
     * @param duelId the duel to accept
     */
    @Transactional
    public void accept(UUID userId, UUID duelId) {
        applyEvent(userId, duelId, DuelEvent.ACCEPT);
    }

    /**
     * Rejects a PENDING duel. Only the opponent may reject; rejection moves the
     * duel to REJECTED (requirements 3.9, 3.10).
     *
     * @param userId the acting user id (JWT {@code sub}); must be the opponent
     * @param duelId the duel to reject
     */
    @Transactional
    public void reject(UUID userId, UUID duelId) {
        applyEvent(userId, duelId, DuelEvent.REJECT);
    }

    /**
     * Lists the authenticated user's duels (as challenger or opponent),
     * optionally filtered by status, within a paged result (requirement 3.12).
     *
     * @param userId the acting user id (JWT {@code sub})
     * @param status an optional status filter; {@code null} for no filter
     * @param page   the pagination request
     * @return a page of {@link DuelSummaryView}
     */
    @Transactional(readOnly = true)
    public PageResult<DuelSummaryView> listMyDuels(UUID userId, DuelStatus status, PageQuery page) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(page, "page must not be null");

        Pageable pageable = PageRequest.of(page.page(), page.size(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<DuelEntity> result = duelRepository.findForUser(userId, status, pageable);

        List<DuelSummaryView> views = result.getContent().stream()
                .map(d -> toSummaryView(userId, d))
                .toList();
        return PageResult.of(views, page.page(), page.size(), result.getTotalElements());
    }

    /**
     * Returns a duel's detail for a participant, gating the opponent's result
     * until the viewer's own status is COMPLETED (requirement 3.6).
     *
     * @param userId the acting user id (JWT {@code sub}); must be a participant
     * @param duelId the duel to read
     * @return a {@link DuelDetailView} with the opponent result present only
     *         when the viewer has completed
     * @throws ForbiddenException if the user is not a participant of the duel
     *                            (also raised when the duel does not exist, to
     *                            avoid leaking existence) (403)
     */
    @Transactional(readOnly = true)
    public DuelDetailView get(UUID userId, UUID duelId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(duelId, "duelId must not be null");

        DuelEntity duel = requireParticipantDuel(userId, duelId);
        DuelParticipantEntity self = participant(duelId, userId);
        DuelParticipantEntity other = participant(duelId, otherUserId(duel, userId));

        DuelDetailView.ParticipantResult selfResult = toResult(self);
        // Gate: opponent detail is only exposed once the viewer is COMPLETED.
        DuelDetailView.ParticipantResult opponentResult =
                self.getStatus() == ParticipantStatus.COMPLETED ? toResult(other) : null;

        return new DuelDetailView(
                duel.getId(), duel.getChallengerId(), duel.getOpponentId(),
                duel.getLanguageId(), duel.getStatus(), duel.getWinnerId(),
                duel.getExpiresAt(), userId, selfResult, opponentResult);
    }

    /**
     * Returns the shared exercise set for the duel and starts the requesting
     * participant. Driving the {@link DuelEvent#START} event transitions an
     * ACCEPTED duel to IN_PROGRESS and the requesting participant to IN_PROGRESS;
     * both participants receive the same exercises in the same order
     * (requirement 3.11).
     *
     * @param userId the acting user id (JWT {@code sub}); must be a participant
     * @param duelId the duel whose exercises are requested
     * @return the duel exercises in presentation order
     * @throws ForbiddenException if the user is not a participant (403)
     */
    @Transactional
    public List<ExerciseView> exercises(UUID userId, UUID duelId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(duelId, "duelId must not be null");

        DuelEntity duelEntity = requireParticipantDuel(userId, duelId);
        DuelParticipantEntity challengerEntity = participant(duelId, duelEntity.getChallengerId());
        DuelParticipantEntity opponentEntity = participant(duelId, duelEntity.getOpponentId());

        // Load the pure-domain aggregate, drive START through the state machine,
        // then write the mutated status/participant state back and persist.
        Duel duel = toDomain(duelEntity, challengerEntity, opponentEntity);
        stateMachine.apply(duel, DuelEvent.START, userId);
        persist(duel, duelEntity, challengerEntity, opponentEntity);

        return learningDuelPort.fetchExercises(duelEntity.getLearningSetId()).stream()
                .map(DuelService::toExerciseView)
                .toList();
    }

    // --- internals ---------------------------------------------------------

    /**
     * Loads the duel and its participants, applies the given event through the
     * state machine (which enforces opponent-only authorization and expiry),
     * then persists the mutated aggregate.
     */
    private void applyEvent(UUID userId, UUID duelId, DuelEvent event) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(duelId, "duelId must not be null");

        DuelEntity duelEntity = requireParticipantDuel(userId, duelId);
        DuelParticipantEntity challengerEntity = participant(duelId, duelEntity.getChallengerId());
        DuelParticipantEntity opponentEntity = participant(duelId, duelEntity.getOpponentId());

        Duel duel = toDomain(duelEntity, challengerEntity, opponentEntity);
        stateMachine.apply(duel, event, userId);
        persist(duel, duelEntity, challengerEntity, opponentEntity);
    }

    /**
     * Loads a duel that the user participates in. A missing duel or a
     * non-participant is reported as {@link ForbiddenException} so the service
     * never discloses the existence of duels the caller is not part of.
     */
    private DuelEntity requireParticipantDuel(UUID userId, UUID duelId) {
        DuelEntity duel = duelRepository.findById(duelId)
                .orElseThrow(() -> new ForbiddenException("not a participant of this duel"));
        if (!userId.equals(duel.getChallengerId()) && !userId.equals(duel.getOpponentId())) {
            throw new ForbiddenException("not a participant of this duel");
        }
        return duel;
    }

    private DuelParticipantEntity participant(UUID duelId, UUID userId) {
        return participantRepository.findByDuelIdAndUserId(duelId, userId)
                .orElseThrow(() -> new IllegalStateException(
                        "duel " + duelId + " is missing participant row for " + userId));
    }

    private static UUID otherUserId(DuelEntity duel, UUID userId) {
        return userId.equals(duel.getChallengerId()) ? duel.getOpponentId() : duel.getChallengerId();
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
     * within the transaction. The optimistic-lock {@code @Version} on the
     * participant rows guards against concurrent writes; a lost check is
     * surfaced as {@link ConcurrentUpdateException} (HTTP 409).
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

    private DuelSummaryView toSummaryView(UUID userId, DuelEntity duel) {
        ParticipantStatus myStatus = participantRepository
                .findByDuelIdAndUserId(duel.getId(), userId)
                .map(DuelParticipantEntity::getStatus)
                .orElse(ParticipantStatus.NOT_STARTED);
        return new DuelSummaryView(
                duel.getId(), duel.getChallengerId(), duel.getOpponentId(),
                duel.getLanguageId(), duel.getStatus(), myStatus,
                duel.getWinnerId(), duel.getExpiresAt());
    }

    private static DuelDetailView.ParticipantResult toResult(DuelParticipantEntity e) {
        return new DuelDetailView.ParticipantResult(
                e.getUserId(), e.getStatus(), e.getScore(), e.getAnsweredCount());
    }

    private static ExerciseView toExerciseView(ExerciseRef ref) {
        List<ExerciseView.Option> options = ref.options().stream()
                .map(o -> new ExerciseView.Option(o.id(), o.text()))
                .toList();
        return new ExerciseView(
                ref.position(), ref.exerciseId(), ref.type(), ref.prompt(), options, ref.maxPoints());
    }
}
