package hu.bme.szoftarch.social.application.friend;

import hu.bme.szoftarch.social.application.common.PageQuery;
import hu.bme.szoftarch.social.application.common.PageResult;
import hu.bme.szoftarch.social.domain.common.ForbiddenException;
import hu.bme.szoftarch.social.domain.friend.FriendPair;
import hu.bme.szoftarch.social.domain.port.IdentityStatsPort;
import hu.bme.szoftarch.social.domain.port.PublicStats;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case service for the authenticated user's friendships: list friends with
 * their public stats and remove a friendship symmetrically.
 *
 * <p>{@code listFriends} pages the user's stored friendships, collects the
 * "other" member of each ordered pair, and aggregates public stats from
 * Identity's batch API. The friend-id set is chunked into batches of at most
 * {@value #IDENTITY_BATCH_LIMIT} ids (requirement 2.3) before the stats are
 * fetched. {@code removeFriend} deletes the single canonical ordered-pair row,
 * which removes the friendship for both users (requirement 2.4).
 *
 * <p>The acting user id is always the {@code sub}-sourced id passed in by the
 * API layer (requirement 4.1); removal is rejected with {@link ForbiddenException}
 * when the actor does not belong to the pair (requirement 2.5).
 */
@Service
public class FriendService {

    /** Identity batch-stats limit: at most 100 user ids per call (requirement 2.3). */
    static final int IDENTITY_BATCH_LIMIT = 100;

    private final FriendshipRepository friendshipRepository;
    private final IdentityStatsPort identityStatsPort;

    public FriendService(FriendshipRepository friendshipRepository,
                         IdentityStatsPort identityStatsPort) {
        this.friendshipRepository = Objects.requireNonNull(friendshipRepository);
        this.identityStatsPort = Objects.requireNonNull(identityStatsPort);
    }

    /**
     * Lists the authenticated user's friends with their public stats, paged.
     *
     * @param userId the acting user id (JWT {@code sub})
     * @param page   the pagination request
     * @return a page of {@link FriendView}; stats are {@code null}/zero for any
     *         friend id Identity has no record for
     */
    @Transactional(readOnly = true)
    public PageResult<FriendView> listFriends(UUID userId, PageQuery page) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(page, "page must not be null");

        Pageable pageable = PageRequest.of(page.page(), page.size(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<FriendshipEntity> friendships = friendshipRepository.findAllForUser(userId, pageable);

        // Collect the "other" member of each pair, preserving page order.
        Set<UUID> otherIds = new LinkedHashSet<>();
        for (FriendshipEntity f : friendships.getContent()) {
            otherIds.add(otherMember(userId, f));
        }

        Map<UUID, PublicStats> stats = fetchStatsChunked(otherIds);

        List<FriendView> views = new ArrayList<>(friendships.getNumberOfElements());
        for (FriendshipEntity f : friendships.getContent()) {
            UUID otherId = otherMember(userId, f);
            PublicStats s = stats.get(otherId);
            if (s == null) {
                views.add(new FriendView(otherId, null, 0L, 0, f.getCreatedAt()));
            } else {
                views.add(new FriendView(otherId, s.displayName(), s.points(), s.level(), f.getCreatedAt()));
            }
        }

        return PageResult.of(views, page.page(), page.size(), friendships.getTotalElements());
    }

    /**
     * Removes the friendship between the authenticated user and {@code friendId}.
     *
     * <p>The friendship is stored once as a canonical ordered pair, so a single
     * delete removes it for both users (symmetric removal).
     *
     * @param userId   the acting user id (JWT {@code sub}); must be a member
     * @param friendId the other member of the friendship to remove
     * @throws ForbiddenException if {@code userId == friendId} or the actor does
     *                            not belong to the (normalized) pair (403)
     */
    @Transactional
    public void removeFriend(UUID userId, UUID friendId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(friendId, "friendId must not be null");

        if (userId.equals(friendId)) {
            // A user can never be a member of a friendship with itself.
            throw new ForbiddenException("cannot remove a friendship you do not belong to");
        }

        FriendPair pair = FriendPair.of(userId, friendId);
        // The actor is derived from the sub only; it is always one member of
        // the pair here, but the delete is a no-op (0 rows) when no such
        // friendship exists, which we surface as FORBIDDEN rather than leak
        // existence.
        long deleted = friendshipRepository.deleteByUserLowIdAndUserHighId(pair.low(), pair.high());
        if (deleted == 0) {
            throw new ForbiddenException("cannot remove a friendship you do not belong to");
        }
    }

    private static UUID otherMember(UUID userId, FriendshipEntity f) {
        return f.getUserLowId().equals(userId) ? f.getUserHighId() : f.getUserLowId();
    }

    /**
     * Fetches public stats for the given friend ids, chunking the set into
     * batches of at most {@value #IDENTITY_BATCH_LIMIT} ids per call and merging
     * the results (requirement 2.3).
     */
    private Map<UUID, PublicStats> fetchStatsChunked(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PublicStats> merged = new LinkedHashMap<>();
        List<UUID> all = new ArrayList<>(ids);
        for (int i = 0; i < all.size(); i += IDENTITY_BATCH_LIMIT) {
            Set<UUID> chunk = new LinkedHashSet<>(
                    all.subList(i, Math.min(i + IDENTITY_BATCH_LIMIT, all.size())));
            merged.putAll(identityStatsPort.batchStats(chunk));
        }
        return merged;
    }
}
