package hu.bme.szoftarch.social.application.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hu.bme.szoftarch.social.application.common.PageQuery;
import hu.bme.szoftarch.social.application.common.PageResult;
import hu.bme.szoftarch.social.domain.common.AlreadyFriendsException;
import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.common.ForbiddenException;
import hu.bme.szoftarch.social.domain.common.RequestExistsException;
import hu.bme.szoftarch.social.domain.common.SelfRequestException;
import hu.bme.szoftarch.social.domain.common.UserNotFoundException;
import hu.bme.szoftarch.social.domain.friend.FriendPair;
import hu.bme.szoftarch.social.domain.friend.FriendRequestStatus;
import hu.bme.szoftarch.social.domain.port.IdentityStatsPort;
import hu.bme.szoftarch.social.domain.port.PublicStats;
import hu.bme.szoftarch.social.domain.port.UserExistencePort;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendRequestEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendRequestRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for the friend use-case services ({@link FriendRequestService} and
 * {@link FriendService}) with all ports and repositories mocked (no Spring
 * context).
 *
 * <p>These tests exercise two design correctness properties:
 * <ul>
 *   <li><b>Property 2 (Single pending request)</b>: a new request is rejected
 *       when a {@code PENDING} request already exists for the unordered pair in
 *       either direction, and on the partial-unique index race.
 *       <b>Validates: Requirements 1.2</b></li>
 *   <li><b>Property 11 (Identity of actor)</b>: authorization for accept/reject
 *       and removal is decided solely by the {@code sub}-sourced {@code userId}
 *       argument, never by an id carried inside the request/DTO.
 *       <b>Validates: Requirements 4.1</b></li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FriendUseCasesTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RECEIVER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000099");

    // ------------------------------------------------------------------
    // FriendRequestService: send
    // ------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Send {

        @Mock
        FriendRequestRepository requestRepository;
        @Mock
        FriendshipRepository friendshipRepository;
        @Mock
        UserExistencePort userExistencePort;

        FriendRequestService service() {
            return new FriendRequestService(requestRepository, friendshipRepository,
                    userExistencePort, Clock.fixed(NOW));
        }

        @Test
        void selfRequestIsRejected() {
            // sender == receiver short-circuits before any port/repo is touched.
            assertThatThrownBy(() -> service().send(SENDER, SENDER))
                    .isInstanceOf(SelfRequestException.class);

            verifyNoInteractions(userExistencePort, friendshipRepository);
            verify(requestRepository, never()).saveAndFlush(any());
        }

        @Test
        void unknownReceiverIsRejected() {
            when(userExistencePort.exists(RECEIVER)).thenReturn(false);

            assertThatThrownBy(() -> service().send(SENDER, RECEIVER))
                    .isInstanceOf(UserNotFoundException.class);

            verify(requestRepository, never()).saveAndFlush(any());
        }

        @Test
        void existingFriendshipIsRejected() {
            when(userExistencePort.exists(RECEIVER)).thenReturn(true);
            FriendPair pair = FriendPair.of(SENDER, RECEIVER);
            when(friendshipRepository.existsByUserLowIdAndUserHighId(pair.low(), pair.high()))
                    .thenReturn(true);

            assertThatThrownBy(() -> service().send(SENDER, RECEIVER))
                    .isInstanceOf(AlreadyFriendsException.class);

            verify(requestRepository, never()).saveAndFlush(any());
        }

        /**
         * Property 2: a pending request already exists in the SAME direction
         * (sender→receiver). The repository detects any pending request between
         * the two users; a second request for the pair must be rejected.
         *
         * <p>Validates: Requirements 1.2 / Property 2
         */
        @Test
        void pendingRequestSameDirectionIsRejected() {
            when(userExistencePort.exists(RECEIVER)).thenReturn(true);
            FriendPair pair = FriendPair.of(SENDER, RECEIVER);
            when(friendshipRepository.existsByUserLowIdAndUserHighId(pair.low(), pair.high()))
                    .thenReturn(false);
            when(requestRepository.existsPendingBetween(SENDER, RECEIVER)).thenReturn(true);

            assertThatThrownBy(() -> service().send(SENDER, RECEIVER))
                    .isInstanceOf(RequestExistsException.class);

            verify(requestRepository, never()).saveAndFlush(any());
        }

        /**
         * Property 2: a pending request already exists in the REVERSE direction
         * (receiver→sender). The pending check is direction-independent over the
         * unordered pair, so the reverse request must also be rejected,
         * guaranteeing at most one pending request per unordered pair.
         *
         * <p>Validates: Requirements 1.2 / Property 2
         */
        @Test
        void pendingRequestReverseDirectionIsRejected() {
            // The caller now acts as RECEIVER sending to SENDER; a pending
            // SENDER→RECEIVER request already exists, so existsPendingBetween
            // (queried either way) reports true for the unordered pair.
            when(userExistencePort.exists(SENDER)).thenReturn(true);
            FriendPair pair = FriendPair.of(RECEIVER, SENDER);
            when(friendshipRepository.existsByUserLowIdAndUserHighId(pair.low(), pair.high()))
                    .thenReturn(false);
            when(requestRepository.existsPendingBetween(RECEIVER, SENDER)).thenReturn(true);

            assertThatThrownBy(() -> service().send(RECEIVER, SENDER))
                    .isInstanceOf(RequestExistsException.class);

            verify(requestRepository, never()).saveAndFlush(any());
        }

        /**
         * Property 2: a concurrent create slips past the application-level
         * pending check and violates the partial-unique PENDING index. The
         * service maps the resulting {@link DataIntegrityViolationException} to
         * {@link RequestExistsException}, preserving the single-pending invariant
         * under a race.
         *
         * <p>Validates: Requirements 1.2 / Property 2
         */
        @Test
        void saveRaceMapsToRequestExists() {
            when(userExistencePort.exists(RECEIVER)).thenReturn(true);
            FriendPair pair = FriendPair.of(SENDER, RECEIVER);
            when(friendshipRepository.existsByUserLowIdAndUserHighId(pair.low(), pair.high()))
                    .thenReturn(false);
            when(requestRepository.existsPendingBetween(SENDER, RECEIVER)).thenReturn(false);
            when(requestRepository.saveAndFlush(any(FriendRequestEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate pending"));

            assertThatThrownBy(() -> service().send(SENDER, RECEIVER))
                    .isInstanceOf(RequestExistsException.class);
        }

        @Test
        void happyPathPersistsPendingRequest() {
            when(userExistencePort.exists(RECEIVER)).thenReturn(true);
            FriendPair pair = FriendPair.of(SENDER, RECEIVER);
            when(friendshipRepository.existsByUserLowIdAndUserHighId(pair.low(), pair.high()))
                    .thenReturn(false);
            when(requestRepository.existsPendingBetween(SENDER, RECEIVER)).thenReturn(false);
            when(requestRepository.saveAndFlush(any(FriendRequestEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            FriendRequestView view = service().send(SENDER, RECEIVER);

            assertThat(view.senderId()).isEqualTo(SENDER);
            assertThat(view.receiverId()).isEqualTo(RECEIVER);
            assertThat(view.status()).isEqualTo(FriendRequestStatus.PENDING);
            assertThat(view.createdAt()).isEqualTo(NOW);
            assertThat(view.updatedAt()).isEqualTo(NOW);

            ArgumentCaptor<FriendRequestEntity> captor =
                    ArgumentCaptor.forClass(FriendRequestEntity.class);
            verify(requestRepository).saveAndFlush(captor.capture());
            FriendRequestEntity persisted = captor.getValue();
            assertThat(persisted.getSenderId()).isEqualTo(SENDER);
            assertThat(persisted.getReceiverId()).isEqualTo(RECEIVER);
            assertThat(persisted.getStatus()).isEqualTo(FriendRequestStatus.PENDING);
            // No friendship is created on a mere send.
            verify(friendshipRepository, never()).saveAndFlush(any());
        }
    }

    // ------------------------------------------------------------------
    // FriendRequestService: accept / reject (Property 11 — actor from userId)
    // ------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class AcceptReject {

        @Mock
        FriendRequestRepository requestRepository;
        @Mock
        FriendshipRepository friendshipRepository;
        @Mock
        UserExistencePort userExistencePort;

        FriendRequestService service() {
            return new FriendRequestService(requestRepository, friendshipRepository,
                    userExistencePort, Clock.fixed(NOW));
        }

        private FriendRequestEntity pendingRequest(UUID id) {
            return new FriendRequestEntity(id, SENDER, RECEIVER,
                    FriendRequestStatus.PENDING, NOW, NOW);
        }

        /**
         * Property 11: only the receiver may accept. Authorization is decided by
         * the passed-in {@code userId} (the JWT {@code sub}); the sender stored on
         * the request entity is a DTO/record field and must never grant access.
         * Here the actor is the SENDER (not the receiver) and the request's own
         * fields are untouched, so acceptance is forbidden.
         *
         * <p>Validates: Requirements 4.1 / Property 11
         */
        @Test
        void nonReceiverCannotAccept() {
            UUID requestId = UUID.randomUUID();
            when(requestRepository.findById(requestId))
                    .thenReturn(Optional.of(pendingRequest(requestId)));

            // Actor is the sender — authorization must be derived from userId,
            // never from a field inside the request.
            assertThatThrownBy(() -> service().accept(SENDER, requestId))
                    .isInstanceOf(ForbiddenException.class);

            verify(requestRepository, never()).save(any());
            verify(friendshipRepository, never()).saveAndFlush(any());
        }

        /**
         * Property 11: a stranger who is neither sender nor receiver cannot act,
         * confirming the decision rests on {@code userId} alone.
         *
         * <p>Validates: Requirements 4.1 / Property 11
         */
        @Test
        void strangerCannotReject() {
            UUID requestId = UUID.randomUUID();
            when(requestRepository.findById(requestId))
                    .thenReturn(Optional.of(pendingRequest(requestId)));

            assertThatThrownBy(() -> service().reject(STRANGER, requestId))
                    .isInstanceOf(ForbiddenException.class);

            verify(requestRepository, never()).save(any());
        }

        @Test
        void receiverAcceptMarksAcceptedAndCreatesFriendship() {
            UUID requestId = UUID.randomUUID();
            when(requestRepository.findById(requestId))
                    .thenReturn(Optional.of(pendingRequest(requestId)));

            FriendRequestService service = service();
            service.accept(RECEIVER, requestId);

            ArgumentCaptor<FriendRequestEntity> reqCaptor =
                    ArgumentCaptor.forClass(FriendRequestEntity.class);
            verify(requestRepository).save(reqCaptor.capture());
            assertThat(reqCaptor.getValue().getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
            assertThat(reqCaptor.getValue().getUpdatedAt()).isEqualTo(NOW);

            ArgumentCaptor<FriendshipEntity> fsCaptor =
                    ArgumentCaptor.forClass(FriendshipEntity.class);
            verify(friendshipRepository).saveAndFlush(fsCaptor.capture());
            FriendPair pair = FriendPair.of(SENDER, RECEIVER);
            assertThat(fsCaptor.getValue().getUserLowId()).isEqualTo(pair.low());
            assertThat(fsCaptor.getValue().getUserHighId()).isEqualTo(pair.high());
        }

        @Test
        void receiverRejectMarksRejectedWithoutFriendship() {
            UUID requestId = UUID.randomUUID();
            when(requestRepository.findById(requestId))
                    .thenReturn(Optional.of(pendingRequest(requestId)));

            service().reject(RECEIVER, requestId);

            ArgumentCaptor<FriendRequestEntity> reqCaptor =
                    ArgumentCaptor.forClass(FriendRequestEntity.class);
            verify(requestRepository).save(reqCaptor.capture());
            assertThat(reqCaptor.getValue().getStatus()).isEqualTo(FriendRequestStatus.REJECTED);
            verify(friendshipRepository, never()).saveAndFlush(any());
        }

        @Test
        void acceptMissingRequestIsRejected() {
            UUID requestId = UUID.randomUUID();
            when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().accept(RECEIVER, requestId))
                    .isInstanceOf(RequestExistsException.class);
        }

        @Test
        void acceptNonPendingRequestIsRejected() {
            UUID requestId = UUID.randomUUID();
            FriendRequestEntity accepted = new FriendRequestEntity(
                    requestId, SENDER, RECEIVER, FriendRequestStatus.ACCEPTED, NOW, NOW);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(accepted));

            assertThatThrownBy(() -> service().accept(RECEIVER, requestId))
                    .isInstanceOf(RequestExistsException.class);

            verify(friendshipRepository, never()).saveAndFlush(any());
        }
    }

    // ------------------------------------------------------------------
    // FriendService: removeFriend (Property 11 — actor from userId)
    // ------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class RemoveFriend {

        @Mock
        FriendshipRepository friendshipRepository;
        @Mock
        IdentityStatsPort identityStatsPort;

        FriendService service() {
            return new FriendService(friendshipRepository, identityStatsPort);
        }

        @Test
        void selfRemovalIsForbidden() {
            assertThatThrownBy(() -> service().removeFriend(SENDER, SENDER))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(friendshipRepository);
        }

        /**
         * Property 11: removal authorization uses the acting {@code userId}. When
         * the normalized pair has no stored friendship the delete affects zero
         * rows and the service refuses the operation rather than leaking state.
         *
         * <p>Validates: Requirements 4.1 / Property 11
         */
        @Test
        void removalOfNonMemberIsForbidden() {
            FriendPair pair = FriendPair.of(SENDER, RECEIVER);
            when(friendshipRepository.deleteByUserLowIdAndUserHighId(pair.low(), pair.high()))
                    .thenReturn(0L);

            assertThatThrownBy(() -> service().removeFriend(SENDER, RECEIVER))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void removalOfExistingFriendshipSucceeds() {
            FriendPair pair = FriendPair.of(SENDER, RECEIVER);
            when(friendshipRepository.deleteByUserLowIdAndUserHighId(pair.low(), pair.high()))
                    .thenReturn(1L);

            service().removeFriend(SENDER, RECEIVER);

            verify(friendshipRepository).deleteByUserLowIdAndUserHighId(pair.low(), pair.high());
        }
    }

    // ------------------------------------------------------------------
    // FriendService: listFriends — batch chunking at the 100-id boundary
    // ------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ListFriendsChunking {

        @Mock
        FriendshipRepository friendshipRepository;
        @Mock
        IdentityStatsPort identityStatsPort;

        FriendService service() {
            return new FriendService(friendshipRepository, identityStatsPort);
        }

        /**
         * Builds {@code count} friendships for {@code user}, each pairing the user
         * with a distinct "other" id, and returns both the page and the set of
         * other ids expected to be looked up.
         */
        private List<FriendshipEntity> friendships(UUID user, int count, Set<UUID> othersOut) {
            List<FriendshipEntity> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID other = UUID.randomUUID();
                othersOut.add(other);
                FriendPair pair = FriendPair.of(user, other);
                list.add(new FriendshipEntity(UUID.randomUUID(), pair.low(), pair.high(), NOW));
            }
            return list;
        }

        private void assertChunkedLookup(int friendCount) {
            Set<UUID> expectedOthers = new HashSet<>();
            List<FriendshipEntity> content = friendships(SENDER, friendCount, expectedOthers);
            Page<FriendshipEntity> page =
                    new PageImpl<>(content, Pageable.unpaged(), friendCount);
            when(friendshipRepository.findAllForUser(eq(SENDER), any(Pageable.class)))
                    .thenReturn(page);
            // Echo back stats for whatever chunk is requested.
            when(identityStatsPort.batchStats(any())).thenAnswer(inv -> {
                Set<UUID> chunk = inv.getArgument(0);
                return chunk.stream().collect(java.util.stream.Collectors.toMap(
                        id -> id, id -> new PublicStats(id, "name-" + id, 10L, 1)));
            });

            PageResult<FriendView> result =
                    service().listFriends(SENDER, new PageQuery(0, 100));

            ArgumentCaptor<Set<UUID>> chunkCaptor = ArgumentCaptor.forClass(Set.class);
            verify(identityStatsPort, org.mockito.Mockito.atLeastOnce())
                    .batchStats(chunkCaptor.capture());

            List<Set<UUID>> chunks = chunkCaptor.getAllValues();
            // Expected number of chunks given the ≤100-per-call limit.
            int expectedChunks = (friendCount + 99) / 100;
            assertThat(chunks).hasSize(expectedChunks);

            // Each chunk holds at most 100 ids.
            for (Set<UUID> chunk : chunks) {
                assertThat(chunk.size()).isLessThanOrEqualTo(100);
            }
            // The union of all chunks covers exactly the friend id set, with no
            // id requested twice.
            Set<UUID> union = new HashSet<>();
            int totalRequested = 0;
            for (Set<UUID> chunk : chunks) {
                union.addAll(chunk);
                totalRequested += chunk.size();
            }
            assertThat(union).isEqualTo(expectedOthers);
            assertThat(totalRequested).isEqualTo(expectedOthers.size());

            // Every friend id is represented in the merged result, with stats.
            assertThat(result.items()).hasSize(friendCount);
            for (FriendView v : result.items()) {
                assertThat(expectedOthers).contains(v.userId());
                assertThat(v.displayName()).isEqualTo("name-" + v.userId());
            }
        }

        @Test
        void exactlyOneHundredFriendsUsesSingleChunk() {
            assertChunkedLookup(100);
        }

        @Test
        void oneHundredOneFriendsSplitsIntoTwoChunks() {
            assertChunkedLookup(101);
        }

        @Test
        void oneHundredFiftyFriendsSplitsIntoTwoChunks() {
            assertChunkedLookup(150);
        }

        @Test
        void twoHundredFriendsSplitsIntoTwoFullChunks() {
            assertChunkedLookup(200);
        }

        @Test
        void twoHundredOneFriendsSplitsIntoThreeChunks() {
            assertChunkedLookup(201);
        }
    }
}
