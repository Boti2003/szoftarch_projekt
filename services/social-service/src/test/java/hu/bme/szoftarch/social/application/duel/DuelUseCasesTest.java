package hu.bme.szoftarch.social.application.duel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.common.ConcurrentUpdateException;
import hu.bme.szoftarch.social.domain.common.ForbiddenException;
import hu.bme.szoftarch.social.domain.common.UpstreamUnavailableException;
import hu.bme.szoftarch.social.domain.duel.DuelStateMachine;
import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import hu.bme.szoftarch.social.domain.duel.ScoreResult;
import hu.bme.szoftarch.social.domain.friend.FriendPair;
import hu.bme.szoftarch.social.domain.port.AnswerSubmission;
import hu.bme.szoftarch.social.domain.port.DuelSetRef;
import hu.bme.szoftarch.social.domain.port.ExerciseRef;
import hu.bme.szoftarch.social.domain.port.LearningDuelPort;
import hu.bme.szoftarch.social.infrastructure.learning.IntegrationException;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Unit tests for the duel lifecycle use cases ({@link DuelService}) and the
 * answer orchestration ({@link DuelAnswerOrchestrator}), with all ports and
 * repositories mocked and the <em>real</em> {@link DuelStateMachine} driven by
 * a fixed {@link Clock} (no Spring context). Using the real state machine keeps
 * the transitions realistic (opponent-only authorization, expiry windows,
 * winner determination) while only the outbound ports/repos are stubbed.
 *
 * <p>These tests exercise three design correctness properties:
 * <ul>
 *   <li><b>Property 4 (Friends-only duels)</b>: a duel may only be created
 *       between two distinct accepted friends; a non-friend or self creation is
 *       rejected with {@code FORBIDDEN}. <b>Validates: Requirements 3.1</b></li>
 *   <li><b>Property 9 (Result privacy)</b>: the opponent's detailed result is
 *       withheld from the detail view until the viewer's own participant status
 *       is {@code COMPLETED}. <b>Validates: Requirements 3.6</b></li>
 *   <li><b>Property 12 (No profile points)</b>: scoring a duel answer awards
 *       competition points only and performs no Identity profile-point
 *       mutation. <b>Validates: Requirements 3.7</b></li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DuelUseCasesTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    private static final UUID CHALLENGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OPPONENT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID DUEL_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID LANGUAGE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID LEARNING_SET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock
    private DuelRepository duelRepository;
    @Mock
    private DuelParticipantRepository participantRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private LearningDuelPort learningDuelPort;

    private DuelStateMachine stateMachine;
    private Clock clock;

    private DuelService duelService;
    private DuelAnswerOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW);
        stateMachine = new DuelStateMachine(clock);
        duelService = new DuelService(duelRepository, participantRepository,
                friendshipRepository, learningDuelPort, stateMachine, clock);
        orchestrator = new DuelAnswerOrchestrator(duelRepository, participantRepository,
                learningDuelPort, stateMachine, clock);
    }

    // --- helpers -----------------------------------------------------------

    private static DuelEntity duel(DuelStatus status, Instant expiresAt, UUID winnerId) {
        return new DuelEntity(DUEL_ID, CHALLENGER, OPPONENT, LANGUAGE_ID,
                LEARNING_SET_ID, status, expiresAt, winnerId, NOW, NOW);
    }

    private static DuelParticipantEntity participant(UUID userId, ParticipantStatus status,
                                                     int score, int answeredCount) {
        return new DuelParticipantEntity(DUEL_ID, userId, status, score, answeredCount, null, null);
    }

    private void stubParticipants(DuelParticipantEntity challenger, DuelParticipantEntity opponent) {
        lenient().when(participantRepository.findByDuelIdAndUserId(DUEL_ID, CHALLENGER))
                .thenReturn(Optional.of(challenger));
        lenient().when(participantRepository.findByDuelIdAndUserId(DUEL_ID, OPPONENT))
                .thenReturn(Optional.of(opponent));
    }

    // ======================================================================
    //  create — Property 4: Friends-only duels. Validates: Requirements 3.1
    // ======================================================================

    @Nested
    class Create {

        @Test
        void selfDuelIsForbiddenAndTouchesNoRepositoryOrLearning() {
            assertThatThrownBy(() -> duelService.create(CHALLENGER, CHALLENGER, LANGUAGE_ID))
                    .isInstanceOf(ForbiddenException.class);

            // Property 4: self-duel rejected before any persistence or Learning call.
            verifyNoInteractions(friendshipRepository, learningDuelPort,
                    duelRepository, participantRepository);
        }

        @Test
        void nonFriendsCreationIsForbiddenAndNoDuelSetIsBuilt() {
            FriendPair pair = FriendPair.of(CHALLENGER, OPPONENT);
            when(friendshipRepository.existsByUserLowIdAndUserHighId(pair.low(), pair.high()))
                    .thenReturn(false);

            assertThatThrownBy(() -> duelService.create(CHALLENGER, OPPONENT, LANGUAGE_ID))
                    .isInstanceOf(ForbiddenException.class);

            // Property 4: a non-friend challenge never asks Learning to build a set
            // and never persists a duel.
            verifyNoInteractions(learningDuelPort, duelRepository, participantRepository);
        }

        @Test
        void acceptedFriendsCreatePendingDuelWithFiveExerciseSetAndTwoNotStartedParticipants() {
            FriendPair pair = FriendPair.of(CHALLENGER, OPPONENT);
            when(friendshipRepository.existsByUserLowIdAndUserHighId(pair.low(), pair.high()))
                    .thenReturn(true);
            when(learningDuelPort.createDuelSet(any(UUID.class), eq(LANGUAGE_ID)))
                    .thenReturn(new DuelSetRef(LEARNING_SET_ID, List.of()));

            DuelView view = duelService.create(CHALLENGER, OPPONENT, LANGUAGE_ID);

            // Returned view: PENDING duel carrying the persisted learning set id.
            assertThat(view.status()).isEqualTo(DuelStatus.PENDING);
            assertThat(view.challengerId()).isEqualTo(CHALLENGER);
            assertThat(view.opponentId()).isEqualTo(OPPONENT);
            assertThat(view.languageId()).isEqualTo(LANGUAGE_ID);
            assertThat(view.learningSetId()).isEqualTo(LEARNING_SET_ID);
            // PENDING window is 72h from creation.
            assertThat(view.expiresAt()).isEqualTo(NOW.plus(DuelStateMachine.PENDING_WINDOW));

            // Learning was asked for the fixed 5-exercise set.
            verify(learningDuelPort).createDuelSet(any(UUID.class), eq(LANGUAGE_ID));

            // The duel is persisted PENDING with the learning_set_id recorded.
            ArgumentCaptor<DuelEntity> duelCaptor = ArgumentCaptor.forClass(DuelEntity.class);
            verify(duelRepository).save(duelCaptor.capture());
            DuelEntity saved = duelCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(DuelStatus.PENDING);
            assertThat(saved.getLearningSetId()).isEqualTo(LEARNING_SET_ID);
            assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(DuelStateMachine.PENDING_WINDOW));

            // Both participants are persisted NOT_STARTED with zero progress.
            ArgumentCaptor<DuelParticipantEntity> partCaptor =
                    ArgumentCaptor.forClass(DuelParticipantEntity.class);
            verify(participantRepository, org.mockito.Mockito.times(2)).save(partCaptor.capture());
            List<DuelParticipantEntity> parts = partCaptor.getAllValues();
            assertThat(parts).extracting(DuelParticipantEntity::getUserId)
                    .containsExactlyInAnyOrder(CHALLENGER, OPPONENT);
            assertThat(parts).allSatisfy(p -> {
                assertThat(p.getStatus()).isEqualTo(ParticipantStatus.NOT_STARTED);
                assertThat(p.getScore()).isZero();
                assertThat(p.getAnsweredCount()).isZero();
            });
        }
    }

    // ======================================================================
    //  accept / reject — opponent-only (driven through the state machine)
    // ======================================================================

    @Nested
    class AcceptReject {

        @Test
        void opponentAcceptMovesDuelToAcceptedAndExtendsExpiryToSevenDays() {
            DuelEntity duelEntity = duel(DuelStatus.PENDING, NOW.plus(DuelStateMachine.PENDING_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.NOT_STARTED, 0, 0);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.NOT_STARTED, 0, 0);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);

            duelService.accept(OPPONENT, DUEL_ID);

            ArgumentCaptor<DuelEntity> captor = ArgumentCaptor.forClass(DuelEntity.class);
            verify(duelRepository).save(captor.capture());
            DuelEntity saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(DuelStatus.ACCEPTED);
            assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(DuelStateMachine.ACTIVE_WINDOW));
        }

        @Test
        void opponentRejectMovesDuelToRejected() {
            DuelEntity duelEntity = duel(DuelStatus.PENDING, NOW.plus(DuelStateMachine.PENDING_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.NOT_STARTED, 0, 0);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.NOT_STARTED, 0, 0);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);

            duelService.reject(OPPONENT, DUEL_ID);

            ArgumentCaptor<DuelEntity> captor = ArgumentCaptor.forClass(DuelEntity.class);
            verify(duelRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(DuelStatus.REJECTED);
        }

        @Test
        void challengerCannotAcceptOwnChallenge() {
            DuelEntity duelEntity = duel(DuelStatus.PENDING, NOW.plus(DuelStateMachine.PENDING_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.NOT_STARTED, 0, 0);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.NOT_STARTED, 0, 0);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);

            assertThatThrownBy(() -> duelService.accept(CHALLENGER, DUEL_ID))
                    .isInstanceOf(ForbiddenException.class);
            // Illegal actor never mutates state.
            verify(duelRepository, never()).save(any());
        }

        @Test
        void challengerCannotRejectOwnChallenge() {
            DuelEntity duelEntity = duel(DuelStatus.PENDING, NOW.plus(DuelStateMachine.PENDING_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.NOT_STARTED, 0, 0);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.NOT_STARTED, 0, 0);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);

            assertThatThrownBy(() -> duelService.reject(CHALLENGER, DUEL_ID))
                    .isInstanceOf(ForbiddenException.class);
            verify(duelRepository, never()).save(any());
        }

        @Test
        void strangerCannotActAndDuelExistenceIsNotLeaked() {
            DuelEntity duelEntity = duel(DuelStatus.PENDING, NOW.plus(DuelStateMachine.PENDING_WINDOW), null);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));

            // A non-participant is rejected as FORBIDDEN before any participant load.
            assertThatThrownBy(() -> duelService.accept(STRANGER, DUEL_ID))
                    .isInstanceOf(ForbiddenException.class);
            verify(duelRepository, never()).save(any());
        }
    }

    // ======================================================================
    //  get — Property 9: Result privacy. Validates: Requirements 3.6
    // ======================================================================

    @Nested
    class GetDetail {

        @Test
        void opponentDetailHiddenWhileViewerIsNotCompleted() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            // Viewer (challenger) still IN_PROGRESS; opponent already COMPLETED with a score.
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 3, 2);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.COMPLETED, 9, 5);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);

            DuelDetailView view = duelService.get(CHALLENGER, DUEL_ID);

            // Property 9: own result present, opponent result withheld (null).
            assertThat(view.viewerId()).isEqualTo(CHALLENGER);
            assertThat(view.self()).isNotNull();
            assertThat(view.self().userId()).isEqualTo(CHALLENGER);
            assertThat(view.self().score()).isEqualTo(3);
            assertThat(view.opponent()).isNull();
        }

        @Test
        void opponentDetailRevealedOnceViewerIsCompleted() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.COMPLETED, 7, 5);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 4, 3);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);

            DuelDetailView view = duelService.get(CHALLENGER, DUEL_ID);

            // Property 9: viewer COMPLETED, so the opponent's full result is exposed.
            assertThat(view.self().status()).isEqualTo(ParticipantStatus.COMPLETED);
            assertThat(view.opponent()).isNotNull();
            assertThat(view.opponent().userId()).isEqualTo(OPPONENT);
            assertThat(view.opponent().score()).isEqualTo(4);
            assertThat(view.opponent().answeredCount()).isEqualTo(3);
        }

        @Test
        void nonParticipantCannotReadDuelDetail() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));

            assertThatThrownBy(() -> duelService.get(STRANGER, DUEL_ID))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    // ======================================================================
    //  exercises — ACCEPTED -> IN_PROGRESS; same exercises/order; 403 stranger
    // ======================================================================

    @Nested
    class Exercises {

        @Test
        void startingMovesDuelAndParticipantToInProgressAndReturnsExercisesInOrder() {
            DuelEntity duelEntity = duel(DuelStatus.ACCEPTED, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.NOT_STARTED, 0, 0);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.NOT_STARTED, 0, 0);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);

            UUID e1 = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
            UUID e2 = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
            List<ExerciseRef> refs = List.of(
                    new ExerciseRef(0, e1, "MULTIPLE_CHOICE", "prompt-1",
                            List.of(new ExerciseRef.Option("a", "A"), new ExerciseRef.Option("b", "B")), 10),
                    new ExerciseRef(1, e2, "TRUE_FALSE", "prompt-2", List.of(), 5));
            when(learningDuelPort.fetchExercises(LEARNING_SET_ID)).thenReturn(refs);

            List<ExerciseView> views = duelService.exercises(CHALLENGER, DUEL_ID);

            // Same exercises in the same order, mapped 1:1.
            assertThat(views).extracting(ExerciseView::exerciseId).containsExactly(e1, e2);
            assertThat(views).extracting(ExerciseView::prompt).containsExactly("prompt-1", "prompt-2");
            assertThat(views.get(0).maxPoints()).isEqualTo(10);

            // Duel advanced to IN_PROGRESS and the acting participant started.
            ArgumentCaptor<DuelEntity> duelCaptor = ArgumentCaptor.forClass(DuelEntity.class);
            verify(duelRepository).save(duelCaptor.capture());
            assertThat(duelCaptor.getValue().getStatus()).isEqualTo(DuelStatus.IN_PROGRESS);
            assertThat(challenger.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            assertThat(challenger.getStartedAt()).isEqualTo(NOW);
            // The opponent is not started by the challenger's request.
            assertThat(opponent.getStatus()).isEqualTo(ParticipantStatus.NOT_STARTED);
        }

        @Test
        void nonParticipantCannotFetchExercises() {
            DuelEntity duelEntity = duel(DuelStatus.ACCEPTED, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));

            assertThatThrownBy(() -> duelService.exercises(STRANGER, DUEL_ID))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(learningDuelPort);
        }
    }

    // ======================================================================
    //  submitAnswer — Property 12 (No profile points), Learning failure safety,
    //  Idempotency-Key forwarding, completion, concurrent-update mapping.
    //  Validates: Requirements 3.7, 5.1, 5.2
    // ======================================================================

    @Nested
    class SubmitAnswer {

        private final AnswerSubmission submission =
                new AnswerSubmission(0, Map.of("selectedOptionIds", List.of("a")));
        private final UUID idemKey = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

        @Test
        void successfulScoreAwardsCompetitionPointsOnlyWithNoProfilePointMutation() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 10, 1);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 0, 0);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);
            when(learningDuelPort.scoreAnswer(eq(LEARNING_SET_ID), any(UUID.class), any(AnswerSubmission.class), eq(idemKey)))
                    .thenReturn(new ScoreResult(true, 10));

            AnswerResultView result = orchestrator.submitAnswer(CHALLENGER, DUEL_ID, submission, idemKey);

            // Only the competition score advanced: +10 points, +1 answered.
            assertThat(result.correct()).isTrue();
            assertThat(result.awardedPoints()).isEqualTo(10);
            assertThat(result.newScore()).isEqualTo(20);
            assertThat(result.answeredCount()).isEqualTo(2);
            assertThat(challenger.getScore()).isEqualTo(20);
            assertThat(challenger.getAnsweredCount()).isEqualTo(2);

            // Property 12: the only outbound port touched is Learning (scoring);
            // there is no Identity write port and no profile-point call is made.
            // The orchestrator was constructed with exactly these collaborators;
            // confirming no unexpected port exists / is called:
            verify(learningDuelPort).scoreAnswer(eq(LEARNING_SET_ID), any(UUID.class), any(AnswerSubmission.class), eq(idemKey));
            org.mockito.Mockito.verifyNoMoreInteractions(learningDuelPort);
            // FriendshipRepository (the only other port-like collaborator in the
            // duel package) is never wired into the orchestrator at all.
            verifyNoInteractions(friendshipRepository);
        }

        @Test
        void learningFailureSurfacesUpstreamUnavailableAndLeavesParticipantStateUnchanged() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 10, 1);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 0, 0);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);
            when(learningDuelPort.scoreAnswer(eq(LEARNING_SET_ID), any(UUID.class), any(AnswerSubmission.class), eq(idemKey)))
                    .thenThrow(new IntegrationException("learning timed out"));

            assertThatThrownBy(() -> orchestrator.submitAnswer(CHALLENGER, DUEL_ID, submission, idemKey))
                    .isInstanceOf(UpstreamUnavailableException.class);

            // Property 10 / Req 5.1: score, answeredCount, status unchanged and
            // nothing is persisted (no advance on Learning failure).
            assertThat(challenger.getScore()).isEqualTo(10);
            assertThat(challenger.getAnsweredCount()).isEqualTo(1);
            assertThat(challenger.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            verify(duelRepository, never()).save(any());
            verify(participantRepository, never()).save(any());
        }

        @Test
        void idempotencyKeyIsForwardedToLearningScoreAnswer() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 0, 0);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 0, 0);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);
            when(learningDuelPort.scoreAnswer(any(), any(), any(), any()))
                    .thenReturn(new ScoreResult(false, 0));

            orchestrator.submitAnswer(CHALLENGER, DUEL_ID, submission, idemKey);

            // Req 5.2: the caller's Idempotency-Key is forwarded verbatim.
            ArgumentCaptor<UUID> keyCaptor = ArgumentCaptor.forClass(UUID.class);
            verify(learningDuelPort).scoreAnswer(eq(LEARNING_SET_ID), any(UUID.class), any(AnswerSubmission.class), keyCaptor.capture());
            assertThat(keyCaptor.getValue()).isEqualTo(idemKey);
        }

        @Test
        void fifthCorrectAnswerCompletesParticipantAndCompletesDuelWithWinnerWhenBothDone() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            // Challenger about to answer their 5th (already 4 answered, score 40).
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 40, 4);
            // Opponent already COMPLETED with a lower score, so challenger wins.
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.COMPLETED, 20, 5);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);
            when(learningDuelPort.scoreAnswer(eq(LEARNING_SET_ID), any(UUID.class), any(AnswerSubmission.class), eq(idemKey)))
                    .thenReturn(new ScoreResult(true, 10));

            AnswerResultView result = orchestrator.submitAnswer(CHALLENGER, DUEL_ID, submission, idemKey);

            // Participant completed at the 5th answer; duel completed; winner set.
            assertThat(result.participantStatus()).isEqualTo(ParticipantStatus.COMPLETED);
            assertThat(result.duelStatus()).isEqualTo(DuelStatus.COMPLETED);
            assertThat(result.winnerId()).isEqualTo(CHALLENGER);
            assertThat(result.newScore()).isEqualTo(50);
            assertThat(result.answeredCount()).isEqualTo(5);

            ArgumentCaptor<DuelEntity> duelCaptor = ArgumentCaptor.forClass(DuelEntity.class);
            verify(duelRepository).save(duelCaptor.capture());
            DuelEntity saved = duelCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(DuelStatus.COMPLETED);
            assertThat(saved.getWinnerId()).isEqualTo(CHALLENGER);
            assertThat(challenger.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
        }

        @Test
        void fifthAnswerCompletesParticipantButNotDuelWhileOpponentStillInProgress() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 40, 4);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 10, 2);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);
            when(learningDuelPort.scoreAnswer(eq(LEARNING_SET_ID), any(UUID.class), any(AnswerSubmission.class), eq(idemKey)))
                    .thenReturn(new ScoreResult(true, 10));

            AnswerResultView result = orchestrator.submitAnswer(CHALLENGER, DUEL_ID, submission, idemKey);

            // Participant done, but duel stays IN_PROGRESS until the opponent finishes.
            assertThat(result.participantStatus()).isEqualTo(ParticipantStatus.COMPLETED);
            assertThat(result.duelStatus()).isEqualTo(DuelStatus.IN_PROGRESS);
            assertThat(result.winnerId()).isNull();
        }

        @Test
        void optimisticLockFailureOnSaveMapsToConcurrentUpdate() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            DuelParticipantEntity challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 0, 0);
            DuelParticipantEntity opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 0, 0);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));
            stubParticipants(challenger, opponent);
            when(learningDuelPort.scoreAnswer(eq(LEARNING_SET_ID), any(UUID.class), any(AnswerSubmission.class), eq(idemKey)))
                    .thenReturn(new ScoreResult(true, 10));
            when(duelRepository.save(any()))
                    .thenThrow(new OptimisticLockingFailureException("version conflict"));

            assertThatThrownBy(() -> orchestrator.submitAnswer(CHALLENGER, DUEL_ID, submission, idemKey))
                    .isInstanceOf(ConcurrentUpdateException.class);
        }

        @Test
        void nonParticipantCannotSubmitAnswer() {
            DuelEntity duelEntity = duel(DuelStatus.IN_PROGRESS, NOW.plus(DuelStateMachine.ACTIVE_WINDOW), null);
            when(duelRepository.findById(DUEL_ID)).thenReturn(Optional.of(duelEntity));

            assertThatThrownBy(() -> orchestrator.submitAnswer(STRANGER, DUEL_ID, submission, idemKey))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(learningDuelPort);
        }
    }
}
