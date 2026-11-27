package hu.bme.szoftarch.social.application.notification;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only use-case service backing the polling notification endpoint
 * (requirement 6.1).
 *
 * <p>{@link #summary(UUID)} returns the count and list of the authenticated
 * user's pending <em>incoming</em> friend requests and actionable duels. A duel
 * is actionable when it is either:
 * <ul>
 *   <li>a PENDING duel where the user is the opponent (a challenge they can
 *       accept or reject); or</li>
 *   <li>an IN_PROGRESS duel where the user is a participant and the user's own
 *       participant status is not yet COMPLETED (the duel is awaiting more of
 *       the user's answers).</li>
 * </ul>
 *
 * <p>This service performs <strong>no writes</strong>: it never changes a
 * request or duel status, and it never persists lazy expiry. To avoid surfacing
 * an already-expired duel as actionable, expiry is evaluated <em>in memory
 * only</em> (a PENDING/IN_PROGRESS duel at or past its {@code expiresAt} is
 * treated as expired and excluded), matching the state machine's expiry rule
 * without mutating the stored row. The method is annotated
 * {@link Transactional}{@code (readOnly = true)} to keep it a cheap read.
 */
@Service
public class NotificationService {

    /**
     * Upper bound on the number of pending requests / actionable duels loaded
     * for the summary. The notification view is a lightweight poll, not an
     * exhaustive listing; a user is not expected to exceed this many
     * outstanding items, and the bound keeps the query cheap.
     */
    private static final int MAX_ITEMS = 100;

    private final FriendRequestRepository friendRequestRepository;
    private final DuelRepository duelRepository;
    private final DuelParticipantRepository duelParticipantRepository;
    private final Clock clock;

    public NotificationService(FriendRequestRepository friendRequestRepository,
                               DuelRepository duelRepository,
                               DuelParticipantRepository duelParticipantRepository,
                               Clock clock) {
        this.friendRequestRepository = Objects.requireNonNull(friendRequestRepository);
        this.duelRepository = Objects.requireNonNull(duelRepository);
        this.duelParticipantRepository = Objects.requireNonNull(duelParticipantRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Builds the read-only notification summary for the authenticated user.
     *
     * @param userId the acting user id (JWT {@code sub}); must not be {@code null}
     * @return the pending-incoming-request and actionable-duel summary
     * @throws NullPointerException if {@code userId} is {@code null}
     */
    @Transactional(readOnly = true)
    public NotificationSummaryView summary(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Instant now = clock.now();

        List<PendingRequestView> pendingRequests = loadPendingIncomingRequests(userId);
        List<ActionableDuelView> actionableDuels = loadActionableDuels(userId, now);

        return new NotificationSummaryView(
                pendingRequests.size(), pendingRequests,
                actionableDuels.size(), actionableDuels);
    }

    /**
     * Loads up to {@link #MAX_ITEMS} pending incoming friend requests
     * (receiver = user, status = PENDING), newest first.
     */
    private List<PendingRequestView> loadPendingIncomingRequests(UUID userId) {
        Pageable pageable = PageRequest.of(0, MAX_ITEMS,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<FriendRequestEntity> page = friendRequestRepository
                .findByReceiverIdAndStatus(userId, FriendRequestStatus.PENDING, pageable);

        List<PendingRequestView> views = new ArrayList<>(page.getNumberOfElements());
        for (FriendRequestEntity request : page.getContent()) {
            views.add(new PendingRequestView(
                    request.getId(), request.getSenderId(), request.getCreatedAt()));
        }
        return views;
    }

    /**
     * Loads the user's actionable duels: PENDING challenges where the user is
     * the opponent, plus IN_PROGRESS duels where the user's own participant
     * status is not yet COMPLETED. Duels past their expiry (evaluated in memory
     * only) are excluded.
     */
    private List<ActionableDuelView> loadActionableDuels(UUID userId, Instant now) {
        List<ActionableDuelView> views = new ArrayList<>();

        // (a) PENDING duels where the user is the opponent (can accept/reject).
        Pageable pendingPage = PageRequest.of(0, MAX_ITEMS,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        for (DuelEntity duel : duelRepository
                .findForUser(userId, DuelStatus.PENDING, pendingPage).getContent()) {
            if (userId.equals(duel.getOpponentId()) && !isExpired(duel, now)) {
                views.add(toView(duel, userId));
            }
        }

        // (b) IN_PROGRESS duels awaiting the user's own answers.
        Pageable inProgressPage = PageRequest.of(0, MAX_ITEMS,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        for (DuelEntity duel : duelRepository
                .findForUser(userId, DuelStatus.IN_PROGRESS, inProgressPage).getContent()) {
            if (isExpired(duel, now)) {
                continue;
            }
            Optional<DuelParticipantEntity> participant =
                    duelParticipantRepository.findByDuelIdAndUserId(duel.getId(), userId);
            boolean awaitingUser = participant
                    .map(p -> p.getStatus() != ParticipantStatus.COMPLETED)
                    .orElse(false);
            if (awaitingUser) {
                views.add(toView(duel, userId));
            }
        }

        return views;
    }

    /**
     * In-memory lazy expiry check mirroring the duel state machine's rule
     * ({@code now >= expiresAt}). Does not persist any change. A duel with no
     * expiry set is treated as not expired.
     */
    private static boolean isExpired(DuelEntity duel, Instant now) {
        Instant expiresAt = duel.getExpiresAt();
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    private static ActionableDuelView toView(DuelEntity duel, UUID userId) {
        UUID otherParticipant = userId.equals(duel.getChallengerId())
                ? duel.getOpponentId()
                : duel.getChallengerId();
        return new ActionableDuelView(
                duel.getId(), duel.getStatus(), otherParticipant, duel.getExpiresAt());
    }
}
