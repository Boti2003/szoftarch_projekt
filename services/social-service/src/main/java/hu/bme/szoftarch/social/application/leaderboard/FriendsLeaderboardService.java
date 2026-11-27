package hu.bme.szoftarch.social.application.leaderboard;

import hu.bme.szoftarch.social.domain.port.IdentityStatsPort;
import hu.bme.szoftarch.social.domain.port.PublicStats;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipRepository;
import java.util.ArrayList;
import java.util.Comparator;
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
 * Use-case service that builds a leaderboard restricted to the authenticated
 * user and their friends (design "FriendsLeaderboardService").
 *
 * <p>The global leaderboard is out of scope (owned by Identity). This service
 * gathers the friend UUIDs from {@code social_db}, adds the authenticated
 * user's own id so they appear in their own ranking (requirement 7.1),
 * batch-fetches public stats from Identity chunked into batches of at most
 * {@value #IDENTITY_BATCH_LIMIT} ids per call (requirement 2.3), and sorts
 * locally by points descending with a stable tie-break on {@code userId}.
 *
 * <p>Social never persists points or profile data; any user (a friend or the
 * caller) for whom Identity has no record is still included with
 * {@code points = 0}, {@code level = 0}, and a {@code null} display name, so
 * the ranking is complete.
 */
@Service
public class FriendsLeaderboardService {

    /** Identity batch-stats limit: at most 100 user ids per call (requirement 2.3). */
    static final int IDENTITY_BATCH_LIMIT = 100;

    /** Page size used to drain all of a user's friendships in bounded fetches. */
    private static final int FRIENDSHIP_PAGE_SIZE = 500;

    private final FriendshipRepository friendshipRepository;
    private final IdentityStatsPort identityStatsPort;

    public FriendsLeaderboardService(FriendshipRepository friendshipRepository,
                                     IdentityStatsPort identityStatsPort) {
        this.friendshipRepository = Objects.requireNonNull(friendshipRepository);
        this.identityStatsPort = Objects.requireNonNull(identityStatsPort);
    }

    /**
     * Builds the leaderboard for the authenticated user and their friends.
     *
     * @param userId the acting user id (JWT {@code sub}); included in the ranking
     * @return the leaderboard entries ordered by points descending with a stable
     *         tie-break on {@code userId}, each carrying its 1-based rank
     */
    @Transactional(readOnly = true)
    public List<LeaderboardEntryView> friendsLeaderboard(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        // Gather all friend ids plus the user themselves (requirement 7.1).
        Set<UUID> participantIds = new LinkedHashSet<>();
        participantIds.add(userId);
        participantIds.addAll(collectAllFriendIds(userId));

        Map<UUID, PublicStats> stats = fetchStatsChunked(participantIds);

        // Build an entry per participant, substituting zeroed placeholder stats
        // when Identity has no record so the ranking stays complete.
        List<LeaderboardEntryView> entries = new ArrayList<>(participantIds.size());
        for (UUID id : participantIds) {
            PublicStats s = stats.get(id);
            if (s == null) {
                entries.add(new LeaderboardEntryView(id, null, 0L, 0, 0));
            } else {
                entries.add(new LeaderboardEntryView(id, s.displayName(), s.points(), s.level(), 0));
            }
        }

        // Sort locally: points descending, stable tie-break on userId ascending.
        entries.sort(Comparator
                .comparingLong(LeaderboardEntryView::points).reversed()
                .thenComparing(LeaderboardEntryView::userId));

        // Assign ranks starting at 1 in sorted order.
        List<LeaderboardEntryView> ranked = new ArrayList<>(entries.size());
        int rank = 1;
        for (LeaderboardEntryView e : entries) {
            ranked.add(new LeaderboardEntryView(e.userId(), e.displayName(), e.points(), e.level(), rank));
            rank++;
        }
        return ranked;
    }

    /**
     * Collects the "other" member of every stored friendship for the user,
     * paging through the repository so the whole friend set is returned (not
     * just a single page).
     */
    private Set<UUID> collectAllFriendIds(UUID userId) {
        Set<UUID> friendIds = new LinkedHashSet<>();
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        int pageNumber = 0;
        Page<FriendshipEntity> page;
        do {
            Pageable pageable = PageRequest.of(pageNumber, FRIENDSHIP_PAGE_SIZE, sort);
            page = friendshipRepository.findAllForUser(userId, pageable);
            for (FriendshipEntity f : page.getContent()) {
                friendIds.add(otherMember(userId, f));
            }
            pageNumber++;
        } while (page.hasNext());
        return friendIds;
    }

    private static UUID otherMember(UUID userId, FriendshipEntity f) {
        return f.getUserLowId().equals(userId) ? f.getUserHighId() : f.getUserLowId();
    }

    /**
     * Fetches public stats for the given ids, chunking the set into batches of
     * at most {@value #IDENTITY_BATCH_LIMIT} ids per call and merging the
     * results (requirement 2.3).
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
