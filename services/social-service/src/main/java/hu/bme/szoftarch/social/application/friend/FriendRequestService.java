package hu.bme.szoftarch.social.application.friend;

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
import hu.bme.szoftarch.social.domain.port.UserExistencePort;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendRequestEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendRequestRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case service for the friend-request lifecycle: send, list, accept, reject.
 *
 * <p>Implements the {@code sendFriendRequest} low-level design: reject a
 * self-request, verify the receiver exists in Identity, reject when a
 * friendship already exists, reject when a {@code PENDING} request already
 * exists in either direction, then persist — mapping a
 * {@link DataIntegrityViolationException} (the race on the partial-unique
 * pending index) to {@link RequestExistsException}.
 *
 * <p>The acting user id is always the {@code sub}-sourced id passed in by the
 * API layer; it is never read from a request body or path (requirement 4.1).
 * Only the receiver of a request may accept or reject it (requirements 1.7–1.9).
 */
@Service
public class FriendRequestService {

    private final FriendRequestRepository requestRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserExistencePort userExistencePort;
    private final Clock clock;

    public FriendRequestService(FriendRequestRepository requestRepository,
                                FriendshipRepository friendshipRepository,
                                UserExistencePort userExistencePort,
                                Clock clock) {
        this.requestRepository = Objects.requireNonNull(requestRepository);
        this.friendshipRepository = Objects.requireNonNull(friendshipRepository);
        this.userExistencePort = Objects.requireNonNull(userExistencePort);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Sends a friend request from {@code senderId} (the authenticated user) to
     * {@code receiverId}.
     *
     * @param senderId   the acting user id (JWT {@code sub})
     * @param receiverId the target user id
     * @return a view of the created PENDING request
     * @throws SelfRequestException    if sender and receiver are equal (400)
     * @throws UserNotFoundException   if the receiver is unknown to Identity (404)
     * @throws AlreadyFriendsException if a friendship already exists (409)
     * @throws RequestExistsException  if a PENDING request already exists in
     *                                 either direction, or on the partial-unique
     *                                 index race (409)
     */
    @Transactional
    public FriendRequestView send(UUID senderId, UUID receiverId) {
        Objects.requireNonNull(senderId, "senderId must not be null");
        Objects.requireNonNull(receiverId, "receiverId must not be null");

        if (senderId.equals(receiverId)) {
            throw new SelfRequestException("cannot send a friend request to yourself");
        }
        if (!userExistencePort.exists(receiverId)) {
            throw new UserNotFoundException("receiver does not exist");
        }

        FriendPair pair = FriendPair.of(senderId, receiverId);
        if (friendshipRepository.existsByUserLowIdAndUserHighId(pair.low(), pair.high())) {
            throw new AlreadyFriendsException("users are already friends");
        }
        if (requestRepository.existsPendingBetween(senderId, receiverId)) {
            throw new RequestExistsException("a pending friend request already exists for this pair");
        }

        Instant now = clock.now();
        FriendRequestEntity entity = new FriendRequestEntity(
                UUID.randomUUID(), senderId, receiverId,
                FriendRequestStatus.PENDING, now, now);
        try {
            FriendRequestEntity saved = requestRepository.saveAndFlush(entity);
            return toView(saved);
        } catch (DataIntegrityViolationException race) {
            // Concurrent create violated the partial-unique PENDING index.
            throw new RequestExistsException("a pending friend request already exists for this pair");
        }
    }

    /**
     * Lists the authenticated user's friend requests filtered by direction and
     * status, within a paged result.
     *
     * @param userId    the acting user id (JWT {@code sub})
     * @param direction {@link Direction#INCOMING} (user is receiver) or
     *                  {@link Direction#OUTGOING} (user is sender)
     * @param status    the request status to filter by
     * @param page      the pagination request
     * @return a page of {@link FriendRequestView}
     */
    @Transactional(readOnly = true)
    public PageResult<FriendRequestView> list(UUID userId, Direction direction,
                                              FriendRequestStatus status, PageQuery page) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(page, "page must not be null");

        Pageable pageable = PageRequest.of(page.page(), page.size(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));

        Page<FriendRequestEntity> result = switch (direction) {
            case INCOMING -> requestRepository.findByReceiverIdAndStatus(userId, status, pageable);
            case OUTGOING -> requestRepository.findBySenderIdAndStatus(userId, status, pageable);
        };

        return PageResult.of(result.map(FriendRequestService::toView).getContent(),
                page.page(), page.size(), result.getTotalElements());
    }

    /**
     * Accepts a PENDING request; only its receiver may do so. Sets the request
     * status to ACCEPTED and creates the corresponding ordered-pair friendship.
     *
     * @param userId    the acting user id (JWT {@code sub}); must be the receiver
     * @param requestId the request to accept
     * @throws ForbiddenException      if {@code userId} is not the receiver (403)
     * @throws RequestExistsException  if the request is not found or not PENDING
     * @throws AlreadyFriendsException if the friendship already exists (409 race)
     */
    @Transactional
    public void accept(UUID userId, UUID requestId) {
        FriendRequestEntity request = loadPendingForReceiver(userId, requestId);
        Instant now = clock.now();
        request.setStatus(FriendRequestStatus.ACCEPTED);
        request.setUpdatedAt(now);
        requestRepository.save(request);

        FriendPair pair = FriendPair.of(request.getSenderId(), request.getReceiverId());
        FriendshipEntity friendship = new FriendshipEntity(
                UUID.randomUUID(), pair.low(), pair.high(), now);
        try {
            friendshipRepository.saveAndFlush(friendship);
        } catch (DataIntegrityViolationException race) {
            // Concurrent accept already created the unique ordered-pair friendship.
            throw new AlreadyFriendsException("users are already friends");
        }
    }

    /**
     * Rejects a PENDING request; only its receiver may do so. Sets the request
     * status to REJECTED and creates no friendship.
     *
     * @param userId    the acting user id (JWT {@code sub}); must be the receiver
     * @param requestId the request to reject
     * @throws ForbiddenException     if {@code userId} is not the receiver (403)
     * @throws RequestExistsException if the request is not found or not PENDING
     */
    @Transactional
    public void reject(UUID userId, UUID requestId) {
        FriendRequestEntity request = loadPendingForReceiver(userId, requestId);
        request.setStatus(FriendRequestStatus.REJECTED);
        request.setUpdatedAt(clock.now());
        requestRepository.save(request);
    }

    private FriendRequestEntity loadPendingForReceiver(UUID userId, UUID requestId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");

        FriendRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RequestExistsException("friend request not found"));
        // Only the receiver may act; authorization uses the sub-sourced id only.
        if (!request.getReceiverId().equals(userId)) {
            throw new ForbiddenException("only the receiver may act on this friend request");
        }
        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new RequestExistsException("friend request is not pending");
        }
        return request;
    }

    private static FriendRequestView toView(FriendRequestEntity e) {
        return new FriendRequestView(
                e.getId(), e.getSenderId(), e.getReceiverId(),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
