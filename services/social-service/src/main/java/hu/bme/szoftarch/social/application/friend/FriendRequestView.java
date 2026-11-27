package hu.bme.szoftarch.social.application.friend;

import hu.bme.szoftarch.social.domain.friend.FriendRequestStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Application-layer read model for a friend request returned to the API layer.
 *
 * <p>Carries the directed sender/receiver ids and lifecycle status so the API
 * layer can render incoming/outgoing listings without reaching into domain or
 * persistence types. All timestamps are UTC.
 *
 * @param id         the request id
 * @param senderId   the sender's user id
 * @param receiverId the receiver's user id
 * @param status     the lifecycle status (PENDING/ACCEPTED/REJECTED)
 * @param createdAt  when the request was created (UTC)
 * @param updatedAt  when the request last changed status (UTC)
 */
public record FriendRequestView(
        UUID id,
        UUID senderId,
        UUID receiverId,
        FriendRequestStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
