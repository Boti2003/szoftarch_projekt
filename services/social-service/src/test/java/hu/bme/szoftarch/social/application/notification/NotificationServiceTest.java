package hu.bme.szoftarch.social.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import hu.bme.szoftarch.social.domain.friend.FriendRequestStatus;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendRequestEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link NotificationService} with all repositories and the
 * {@link Clock} mocked (no Spring context).
 *
 * <p>Covers requirement 6.1's read-only polling summary:
 * <ul>
 *   <li>Pending incoming friend requests (receiver = user, status PENDING) are
 *       counted and listed, with the count equal to the list size.</li>
 *   <li>Actionable duels: PENDING challenges where the user is the opponent are
 *       included; IN_PROGRESS duels where the user's own participant status is
 *       not yet COMPLETED are included; IN_PROGRESS duels where the user's own
 *       status is COMPLETED are excluded; and duels at or past their expiry
 *       (evaluated in memory) are excluded.</li>
 *   <li><b>Read-only behavior</b>: the summary performs no save / delete /
 *       status mutation on any repository, matching requirement 6.1's "without
 *       modifying any stored state".</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Mock
    FriendRequestRepository friendRequestRepository;
    @Mock
    DuelRepository duelRepository;
    @Mock
    DuelParticipantRepository duelParticipantRepository;

    private NotificationService service() {
        return new NotificationService(
                friendRequestRepository, duelRepository, duelParticipantRepository,
                Clock.fixed(NOW));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static FriendRequestEntity pending(UUID sender, UUID receiver) {
        return new FriendRequestEntity(UUID.randomUUID(), sender, receiver,
                FriendRequestStatus.PENDING, NOW, NOW);
    }

    /** A duel with the given status, participants and expiry. */
    private static DuelEntity duel(UUID challenger, UUID opponent, DuelStatus status,
                                   Instant expiresAt) {
        return new DuelEntity(UUID.randomUUID(), challenger, opponent,
                UUID.randomUUID(), UUID.randomUUID(), status, expiresAt, null, NOW, NOW);
    }

    private static DuelParticipantEntity participant(UUID duelId, UUID userId,
                                                     ParticipantStatus status) {
        return new DuelParticipantEntity(duelId, userId, status, 0, 0, NOW, null);
    }

    private static <T> Page<T> page(List<T> content) {
        return new PageImpl<>(content, Pageable.unpaged(), content.size());
    }

    /** Stubs an empty PENDING-as-opponent duel page (lenient: not every test reaches it). */
    private void stubNoPendingDuels() {
        lenient().when(duelRepository.findForUser(eq(USER), eq(DuelStatus.PENDING), any()))
                .thenReturn(page(List.of()));
    }

    /** Stubs an empty IN_PROGRESS duel page (lenient: not every test reaches it). */
    private void stubNoInProgressDuels() {
        lenient().when(duelRepository.findForUser(eq(USER), eq(DuelStatus.IN_PROGRESS), any()))
                .thenReturn(page(List.of()));
    }

    /** Stubs an empty pending-incoming-requests page. */
    private void stubNoPendingRequests() {
        lenient().when(friendRequestRepository.findByReceiverIdAndStatus(
                        eq(USER), eq(FriendRequestStatus.PENDING), any()))
                .thenReturn(page(List.of()));
    }

    // ------------------------------------------------------------------
    // Pending incoming friend requests
    // ------------------------------------------------------------------

    @Test
    void pendingIncomingRequestsAreCountedAndListed() {
        FriendRequestEntity r1 = pending(OTHER, USER);
        FriendRequestEntity r2 = pending(UUID.randomUUID(), USER);
        when(friendRequestRepository.findByReceiverIdAndStatus(
                eq(USER), eq(FriendRequestStatus.PENDING), any()))
                .thenReturn(page(List.of(r1, r2)));
        stubNoPendingDuels();
        stubNoInProgressDuels();

        NotificationSummaryView summary = service().summary(USER);

        assertThat(summary.pendingFriendRequestCount()).isEqualTo(2);
        assertThat(summary.pendingFriendRequests()).hasSize(2);
        assertThat(summary.pendingFriendRequests())
                .extracting(PendingRequestView::requestId)
                .containsExactlyInAnyOrder(r1.getId(), r2.getId());
        assertThat(summary.pendingFriendRequests())
                .extracting(PendingRequestView::senderId)
                .containsExactlyInAnyOrder(r1.getSenderId(), r2.getSenderId());
        // Count always equals the list size.
        assertThat(summary.pendingFriendRequestCount())
                .isEqualTo(summary.pendingFriendRequests().size());
    }

    @Test
    void noPendingRequestsYieldsZeroCount() {
        stubNoPendingRequests();
        stubNoPendingDuels();
        stubNoInProgressDuels();

        NotificationSummaryView summary = service().summary(USER);

        assertThat(summary.pendingFriendRequestCount()).isZero();
        assertThat(summary.pendingFriendRequests()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Actionable duels
    // ------------------------------------------------------------------

    @Test
    void pendingDuelWhereUserIsOpponentIsActionable() {
        stubNoPendingRequests();
        // User is the opponent of a PENDING challenge not yet expired.
        DuelEntity challenge = duel(OTHER, USER, DuelStatus.PENDING, NOW.plusSeconds(3600));
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.PENDING), any()))
                .thenReturn(page(List.of(challenge)));
        stubNoInProgressDuels();

        NotificationSummaryView summary = service().summary(USER);

        assertThat(summary.actionableDuelCount()).isEqualTo(1);
        assertThat(summary.actionableDuels()).hasSize(1);
        ActionableDuelView view = summary.actionableDuels().get(0);
        assertThat(view.duelId()).isEqualTo(challenge.getId());
        assertThat(view.status()).isEqualTo(DuelStatus.PENDING);
        // The "opponentId" field exposes the OTHER participant (here the challenger).
        assertThat(view.opponentId()).isEqualTo(OTHER);
        assertThat(summary.actionableDuelCount())
                .isEqualTo(summary.actionableDuels().size());
    }

    @Test
    void pendingDuelWhereUserIsChallengerIsNotActionable() {
        stubNoPendingRequests();
        // User created the challenge; it is not theirs to accept/reject.
        DuelEntity challenge = duel(USER, OTHER, DuelStatus.PENDING, NOW.plusSeconds(3600));
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.PENDING), any()))
                .thenReturn(page(List.of(challenge)));
        stubNoInProgressDuels();

        NotificationSummaryView summary = service().summary(USER);

        assertThat(summary.actionableDuelCount()).isZero();
        assertThat(summary.actionableDuels()).isEmpty();
    }

    @Test
    void expiredPendingDuelIsExcluded() {
        stubNoPendingRequests();
        // PENDING challenge whose expiry is exactly now => treated as expired.
        DuelEntity expired = duel(OTHER, USER, DuelStatus.PENDING, NOW);
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.PENDING), any()))
                .thenReturn(page(List.of(expired)));
        stubNoInProgressDuels();

        NotificationSummaryView summary = service().summary(USER);

        assertThat(summary.actionableDuels()).isEmpty();
    }

    @Test
    void inProgressDuelWithIncompleteOwnParticipantIsActionable() {
        stubNoPendingRequests();
        stubNoPendingDuels();
        DuelEntity inProgress = duel(USER, OTHER, DuelStatus.IN_PROGRESS, NOW.plusSeconds(3600));
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.IN_PROGRESS), any()))
                .thenReturn(page(List.of(inProgress)));
        when(duelParticipantRepository.findByDuelIdAndUserId(inProgress.getId(), USER))
                .thenReturn(Optional.of(
                        participant(inProgress.getId(), USER, ParticipantStatus.IN_PROGRESS)));

        NotificationSummaryView summary = service().summary(USER);

        assertThat(summary.actionableDuelCount()).isEqualTo(1);
        assertThat(summary.actionableDuels()).hasSize(1);
        ActionableDuelView view = summary.actionableDuels().get(0);
        assertThat(view.duelId()).isEqualTo(inProgress.getId());
        assertThat(view.status()).isEqualTo(DuelStatus.IN_PROGRESS);
        // User is the challenger, so the exposed "opponentId" is the real opponent.
        assertThat(view.opponentId()).isEqualTo(OTHER);
    }

    @Test
    void inProgressDuelWithCompletedOwnParticipantIsExcluded() {
        stubNoPendingRequests();
        stubNoPendingDuels();
        DuelEntity inProgress = duel(USER, OTHER, DuelStatus.IN_PROGRESS, NOW.plusSeconds(3600));
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.IN_PROGRESS), any()))
                .thenReturn(page(List.of(inProgress)));
        when(duelParticipantRepository.findByDuelIdAndUserId(inProgress.getId(), USER))
                .thenReturn(Optional.of(
                        participant(inProgress.getId(), USER, ParticipantStatus.COMPLETED)));

        NotificationSummaryView summary = service().summary(USER);

        assertThat(summary.actionableDuels()).isEmpty();
    }

    @Test
    void expiredInProgressDuelIsExcludedWithoutLoadingParticipant() {
        stubNoPendingRequests();
        stubNoPendingDuels();
        DuelEntity expired = duel(USER, OTHER, DuelStatus.IN_PROGRESS, NOW.minusSeconds(1));
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.IN_PROGRESS), any()))
                .thenReturn(page(List.of(expired)));

        NotificationSummaryView summary = service().summary(USER);

        assertThat(summary.actionableDuels()).isEmpty();
        // Expiry is evaluated before any participant load; no participant query runs.
        verify(duelParticipantRepository, never()).findByDuelIdAndUserId(any(), any());
    }

    @Test
    void countsAndListsCombinePendingAndInProgressDuels() {
        stubNoPendingRequests();
        DuelEntity challenge = duel(OTHER, USER, DuelStatus.PENDING, NOW.plusSeconds(3600));
        DuelEntity inProgress = duel(USER, OTHER, DuelStatus.IN_PROGRESS, NOW.plusSeconds(3600));
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.PENDING), any()))
                .thenReturn(page(List.of(challenge)));
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.IN_PROGRESS), any()))
                .thenReturn(page(List.of(inProgress)));
        when(duelParticipantRepository.findByDuelIdAndUserId(inProgress.getId(), USER))
                .thenReturn(Optional.of(
                        participant(inProgress.getId(), USER, ParticipantStatus.IN_PROGRESS)));

        NotificationSummaryView summary = service().summary(USER);

        assertThat(summary.actionableDuelCount()).isEqualTo(2);
        assertThat(summary.actionableDuels())
                .extracting(ActionableDuelView::duelId)
                .containsExactlyInAnyOrder(challenge.getId(), inProgress.getId());
        assertThat(summary.actionableDuelCount())
                .isEqualTo(summary.actionableDuels().size());
    }

    // ------------------------------------------------------------------
    // Read-only behavior (requirement 6.1: "without modifying any stored state")
    // ------------------------------------------------------------------

    @Test
    void summaryPerformsNoWritesOnAnyRepository() {
        // A representative mix: a pending request, a PENDING challenge, and an
        // IN_PROGRESS duel awaiting the user — the branches that touch every repo.
        when(friendRequestRepository.findByReceiverIdAndStatus(
                eq(USER), eq(FriendRequestStatus.PENDING), any()))
                .thenReturn(page(List.of(pending(OTHER, USER))));
        DuelEntity challenge = duel(OTHER, USER, DuelStatus.PENDING, NOW.plusSeconds(3600));
        DuelEntity inProgress = duel(USER, OTHER, DuelStatus.IN_PROGRESS, NOW.plusSeconds(3600));
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.PENDING), any()))
                .thenReturn(page(List.of(challenge)));
        when(duelRepository.findForUser(eq(USER), eq(DuelStatus.IN_PROGRESS), any()))
                .thenReturn(page(List.of(inProgress)));
        when(duelParticipantRepository.findByDuelIdAndUserId(inProgress.getId(), USER))
                .thenReturn(Optional.of(
                        participant(inProgress.getId(), USER, ParticipantStatus.IN_PROGRESS)));

        service().summary(USER);

        // No write/mutation of any kind on any repository.
        verify(friendRequestRepository, never()).save(any());
        verify(friendRequestRepository, never()).saveAndFlush(any());
        verify(friendRequestRepository, never()).delete(any());
        verify(friendRequestRepository, never()).deleteById(any());

        verify(duelRepository, never()).save(any());
        verify(duelRepository, never()).saveAndFlush(any());
        verify(duelRepository, never()).delete(any());
        verify(duelRepository, never()).deleteById(any());

        verify(duelParticipantRepository, never()).save(any());
        verify(duelParticipantRepository, never()).saveAndFlush(any());
        verify(duelParticipantRepository, never()).delete(any());
        verify(duelParticipantRepository, never()).deleteById(any());
    }
}
