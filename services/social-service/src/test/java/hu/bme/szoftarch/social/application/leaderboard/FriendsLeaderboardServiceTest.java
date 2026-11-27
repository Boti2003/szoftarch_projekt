package hu.bme.szoftarch.social.application.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.bme.szoftarch.social.domain.friend.FriendPair;
import hu.bme.szoftarch.social.domain.port.IdentityStatsPort;
import hu.bme.szoftarch.social.domain.port.PublicStats;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link FriendsLeaderboardService} with the friendship
 * repository and Identity stats port mocked (no Spring context).
 *
 * <p>Covers the leaderboard contract from requirements 2.3 and 7.1:
 * <ul>
 *   <li>Local sort by points descending with a <b>stable tie-break on
 *       {@code userId} ascending</b> and 1-based ranks assigned in sorted
 *       order. (Requirement 7.1)</li>
 *   <li>Chunking of the Identity batch-stats calls at the ≤100-id boundary,
 *       with the union of chunks covering every participant exactly once.
 *       (Requirement 2.3)</li>
 *   <li>Inclusion of the authenticated user in their own ranking, and
 *       inclusion of participants Identity has no record for using zeroed
 *       placeholder stats. (Requirement 7.1)</li>
 *   <li>Paging through {@code findAllForUser} so the whole friend set is
 *       collected across repository pages.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FriendsLeaderboardServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    /** Low-order UUID helper so ids are easy to reason about in tie-break assertions. */
    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    /** A single repository page carrying {@code content}, with no further pages. */
    private static Page<FriendshipEntity> singlePage(List<FriendshipEntity> content) {
        return new PageImpl<>(content, Pageable.unpaged(), content.size());
    }

    /** Builds a stored friendship between the user and {@code other} (normalized pair). */
    private static FriendshipEntity friendship(UUID user, UUID other) {
        FriendPair pair = FriendPair.of(user, other);
        return new FriendshipEntity(UUID.randomUUID(), pair.low(), pair.high(), NOW);
    }

    // ------------------------------------------------------------------
    // Sorting: points descending, stable tie-break on userId ascending
    // ------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Sorting {

        @Mock
        FriendshipRepository friendshipRepository;
        @Mock
        IdentityStatsPort identityStatsPort;

        private FriendsLeaderboardService service() {
            return new FriendsLeaderboardService(friendshipRepository, identityStatsPort);
        }

        /**
         * With several participants, entries must be ordered by points
         * descending; within a block of equal points the tie-break must be
         * {@code userId} ascending, and ranks must be 1-based in that sorted
         * order.
         *
         * <p>Validates: Requirement 7.1
         */
        @Test
        void sortsByPointsDescendingThenUserIdAscendingWithOneBasedRanks() {
            UUID self = id(50);
            // Three friends sharing points=100 with the self id to exercise the
            // tie-break across a block, plus two distinct-points friends.
            UUID tieA = id(10);
            UUID tieB = id(30);
            UUID tieC = id(70);
            UUID high = id(99);
            UUID low = id(5);

            List<FriendshipEntity> content = List.of(
                    friendship(self, tieA),
                    friendship(self, tieB),
                    friendship(self, tieC),
                    friendship(self, high),
                    friendship(self, low));
            when(friendshipRepository.findAllForUser(eq(self), any(Pageable.class)))
                    .thenReturn(singlePage(content));

            Map<UUID, PublicStats> stats = new HashMap<>();
            stats.put(self, new PublicStats(self, "self", 100L, 3));   // tie @100
            stats.put(tieA, new PublicStats(tieA, "a", 100L, 3));      // tie @100
            stats.put(tieB, new PublicStats(tieB, "b", 100L, 3));      // tie @100
            stats.put(tieC, new PublicStats(tieC, "c", 100L, 3));      // tie @100
            stats.put(high, new PublicStats(high, "high", 250L, 9));   // top
            stats.put(low, new PublicStats(low, "low", 5L, 1));        // bottom
            when(identityStatsPort.batchStats(any())).thenReturn(stats);

            List<LeaderboardEntryView> result = service().friendsLeaderboard(self);

            // highest points first; then the four tied @100 ordered by userId asc;
            // then the lowest. tied ids by value: 5<10<30<50<70<99 => within the
            // 100-block the ascending ids are tieA(10), tieB(30), self(50), tieC(70).
            List<UUID> expectedOrder = List.of(high, tieA, tieB, self, tieC, low);
            assertThat(result).extracting(LeaderboardEntryView::userId)
                    .containsExactlyElementsOf(expectedOrder);

            // Points are non-increasing across the whole list.
            for (int i = 1; i < result.size(); i++) {
                assertThat(result.get(i).points())
                        .isLessThanOrEqualTo(result.get(i - 1).points());
            }

            // Within the equal-points block the ids strictly ascend.
            List<UUID> tiedBlock = result.stream()
                    .filter(e -> e.points() == 100L)
                    .map(LeaderboardEntryView::userId)
                    .toList();
            assertThat(tiedBlock).isSortedAccordingTo(Comparator.naturalOrder());

            // Ranks are 1..N in sorted order.
            for (int i = 0; i < result.size(); i++) {
                assertThat(result.get(i).rank()).isEqualTo(i + 1);
            }
        }

        /**
         * The sort is deterministic regardless of the order Identity returns or
         * the repository yields: equal-points entries always tie-break on the
         * ascending userId, never on insertion order.
         *
         * <p>Validates: Requirement 7.1
         */
        @Test
        void tieBreakIsStableOnUserIdRegardlessOfInputOrder() {
            UUID self = id(42);
            UUID f1 = id(80);
            UUID f2 = id(20);
            UUID f3 = id(60);

            when(friendshipRepository.findAllForUser(eq(self), any(Pageable.class)))
                    .thenReturn(singlePage(List.of(
                            friendship(self, f1),
                            friendship(self, f2),
                            friendship(self, f3))));

            // All four share identical points => pure userId tie-break.
            Map<UUID, PublicStats> stats = new HashMap<>();
            for (UUID u : List.of(self, f1, f2, f3)) {
                stats.put(u, new PublicStats(u, "u", 42L, 2));
            }
            when(identityStatsPort.batchStats(any())).thenReturn(stats);

            List<LeaderboardEntryView> result = service().friendsLeaderboard(self);

            assertThat(result).extracting(LeaderboardEntryView::userId)
                    .containsExactly(id(20), id(42), id(60), id(80));
        }
    }

    // ------------------------------------------------------------------
    // Self inclusion and missing-stats placeholder handling
    // ------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class CompletenessAndSelf {

        @Mock
        FriendshipRepository friendshipRepository;
        @Mock
        IdentityStatsPort identityStatsPort;

        private FriendsLeaderboardService service() {
            return new FriendsLeaderboardService(friendshipRepository, identityStatsPort);
        }

        /**
         * The authenticated user is always ranked, even with no friends and even
         * when Identity has their stats.
         *
         * <p>Validates: Requirement 7.1
         */
        @Test
        void authenticatedUserIsIncludedWithNoFriends() {
            UUID self = id(1);
            when(friendshipRepository.findAllForUser(eq(self), any(Pageable.class)))
                    .thenReturn(singlePage(List.of()));
            when(identityStatsPort.batchStats(any()))
                    .thenReturn(Map.of(self, new PublicStats(self, "me", 7L, 1)));

            List<LeaderboardEntryView> result = service().friendsLeaderboard(self);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo(self);
            assertThat(result.get(0).rank()).isEqualTo(1);
            assertThat(result.get(0).points()).isEqualTo(7L);
        }

        /**
         * A participant (friend or self) with no Identity record is still ranked
         * with {@code points = 0}, {@code level = 0} and a {@code null} display
         * name, keeping the ranking complete.
         *
         * <p>Validates: Requirement 7.1
         */
        @Test
        void participantsMissingStatsAreIncludedWithZeroedPlaceholders() {
            UUID self = id(1);
            UUID known = id(2);
            UUID missing = id(3);

            when(friendshipRepository.findAllForUser(eq(self), any(Pageable.class)))
                    .thenReturn(singlePage(List.of(
                            friendship(self, known),
                            friendship(self, missing))));

            // Identity knows the self and the "known" friend, but not "missing".
            Map<UUID, PublicStats> stats = new HashMap<>();
            stats.put(self, new PublicStats(self, "me", 30L, 2));
            stats.put(known, new PublicStats(known, "known", 40L, 3));
            when(identityStatsPort.batchStats(any())).thenReturn(stats);

            List<LeaderboardEntryView> result = service().friendsLeaderboard(self);

            assertThat(result).extracting(LeaderboardEntryView::userId)
                    .containsExactly(known, self, missing);

            LeaderboardEntryView missingEntry = result.stream()
                    .filter(e -> e.userId().equals(missing))
                    .findFirst()
                    .orElseThrow();
            assertThat(missingEntry.points()).isZero();
            assertThat(missingEntry.level()).isZero();
            assertThat(missingEntry.displayName()).isNull();
            assertThat(missingEntry.rank()).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------
    // Chunking at the 100-id boundary (requirement 2.3)
    // ------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Chunking {

        @Mock
        FriendshipRepository friendshipRepository;
        @Mock
        IdentityStatsPort identityStatsPort;

        private FriendsLeaderboardService service() {
            return new FriendsLeaderboardService(friendshipRepository, identityStatsPort);
        }

        /**
         * Drives the leaderboard with {@code friendCount} friends. The
         * participant set is {@code friendCount + 1} (friends plus self); asserts
         * that every batch call carries ≤100 ids, that the chunks' union is
         * exactly the participant set with no id requested twice, and that the
         * merged ranking contains every participant.
         *
         * <p>Validates: Requirement 2.3
         */
        private void assertChunkedLookup(int friendCount) {
            UUID self = UUID.randomUUID();
            Set<UUID> participants = new LinkedHashSet<>();
            participants.add(self);

            List<FriendshipEntity> content = new ArrayList<>(friendCount);
            for (int i = 0; i < friendCount; i++) {
                UUID other = UUID.randomUUID();
                participants.add(other);
                content.add(friendship(self, other));
            }

            when(friendshipRepository.findAllForUser(eq(self), any(Pageable.class)))
                    .thenReturn(singlePage(content));
            // Echo back stats for whatever chunk is requested.
            when(identityStatsPort.batchStats(any())).thenAnswer(inv -> {
                Set<UUID> chunk = inv.getArgument(0);
                Map<UUID, PublicStats> out = new HashMap<>();
                for (UUID u : chunk) {
                    out.put(u, new PublicStats(u, "n", 1L, 1));
                }
                return out;
            });

            List<LeaderboardEntryView> result = service().friendsLeaderboard(self);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<UUID>> chunkCaptor = ArgumentCaptor.forClass(Set.class);
            verify(identityStatsPort, atLeastOnce()).batchStats(chunkCaptor.capture());

            List<Set<UUID>> chunks = chunkCaptor.getAllValues();
            int participantCount = friendCount + 1; // +1 for self
            int expectedChunks = (participantCount + 99) / 100;
            assertThat(chunks).hasSize(expectedChunks);

            Set<UUID> union = new HashSet<>();
            int totalRequested = 0;
            for (Set<UUID> chunk : chunks) {
                assertThat(chunk.size()).isLessThanOrEqualTo(100);
                union.addAll(chunk);
                totalRequested += chunk.size();
            }
            // Union covers everyone, and no id was requested in two chunks.
            assertThat(union).isEqualTo(participants);
            assertThat(totalRequested).isEqualTo(participants.size());

            // The merged ranking includes every participant exactly once.
            assertThat(result).hasSize(participantCount);
            assertThat(result).extracting(LeaderboardEntryView::userId)
                    .containsExactlyInAnyOrderElementsOf(participants);
        }

        @Test
        void exactlyOneHundredParticipantsUseSingleChunk() {
            // 99 friends + self = 100 participants.
            assertChunkedLookup(99);
        }

        @Test
        void oneHundredOneParticipantsSplitIntoTwoChunks() {
            // 100 friends + self = 101 participants, crossing the boundary.
            assertChunkedLookup(100);
        }

        @Test
        void oneHundredFiftyOneParticipantsSplitIntoTwoChunks() {
            // 150 friends + self = 151 participants.
            assertChunkedLookup(150);
        }

        @Test
        void twoHundredOneParticipantsSplitIntoThreeChunks() {
            // 200 friends + self = 201 participants.
            assertChunkedLookup(200);
        }
    }

    // ------------------------------------------------------------------
    // Page-through of findAllForUser across multiple repository pages
    // ------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class FriendPaging {

        @Mock
        FriendshipRepository friendshipRepository;
        @Mock
        IdentityStatsPort identityStatsPort;

        private FriendsLeaderboardService service() {
            return new FriendsLeaderboardService(friendshipRepository, identityStatsPort);
        }

        /**
         * When the repository returns friendships across more than one page, the
         * service must drain every page so that all friends are ranked, not just
         * the first page.
         *
         * <p>Validates: Requirement 7.1
         */
        @Test
        void collectsFriendsAcrossMultipleRepositoryPages() {
            UUID self = UUID.randomUUID();
            UUID f1 = UUID.randomUUID();
            UUID f2 = UUID.randomUUID();
            UUID f3 = UUID.randomUUID();

            // Two pages: [f1, f2] then [f3]. The first page reports hasNext=true
            // via a total that exceeds its own content size.
            Page<FriendshipEntity> page0 = new PageImpl<>(
                    List.of(friendship(self, f1), friendship(self, f2)),
                    Pageable.ofSize(2).withPage(0), 3);
            Page<FriendshipEntity> page1 = new PageImpl<>(
                    List.of(friendship(self, f3)),
                    Pageable.ofSize(2).withPage(1), 3);

            when(friendshipRepository.findAllForUser(eq(self), any(Pageable.class)))
                    .thenReturn(page0, page1);

            when(identityStatsPort.batchStats(any())).thenAnswer(inv -> {
                Set<UUID> chunk = inv.getArgument(0);
                Map<UUID, PublicStats> out = new HashMap<>();
                for (UUID u : chunk) {
                    out.put(u, new PublicStats(u, "n", 1L, 1));
                }
                return out;
            });

            List<LeaderboardEntryView> result = service().friendsLeaderboard(self);

            assertThat(result).extracting(LeaderboardEntryView::userId)
                    .containsExactlyInAnyOrder(self, f1, f2, f3);
            // Both pages were fetched.
            verify(friendshipRepository, times(2))
                    .findAllForUser(eq(self), any(Pageable.class));
        }
    }
}
