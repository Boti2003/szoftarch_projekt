package hu.bme.szoftarch.social.domain.friend;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain representation of a directed friend request.
 *
 * <p>This type carries no persistence or framework concerns: no JPA
 * annotations and no {@code org.springframework.*} imports. It models the
 * request lifecycle (PENDING -&gt; ACCEPTED | REJECTED) and enforces the
 * domain invariant that sender and receiver are distinct.
 *
 * <p>Only the receiver may accept or reject; that authorization check lives in
 * the application layer (which supplies the acting user id from the JWT
 * {@code sub}). This type guards the state-transition legality itself.
 */
public final class FriendRequest {

    private final UUID id;
    private final UUID senderId;
    private final UUID receiverId;
    private FriendRequestStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Reconstitutes a friend request (e.g. when loading from storage).
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code senderId.equals(receiverId)}
     */
    public FriendRequest(UUID id, UUID senderId, UUID receiverId,
                         FriendRequestStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.senderId = Objects.requireNonNull(senderId, "senderId must not be null");
        this.receiverId = Objects.requireNonNull(receiverId, "receiverId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("friend request sender and receiver must differ");
        }
    }

    /**
     * Creates a fresh PENDING friend request from {@code senderId} to
     * {@code receiverId}.
     *
     * @throws IllegalArgumentException if sender and receiver are equal
     */
    public static FriendRequest create(UUID id, UUID senderId, UUID receiverId, Instant now) {
        return new FriendRequest(id, senderId, receiverId, FriendRequestStatus.PENDING, now, now);
    }

    /**
     * Marks a PENDING request as ACCEPTED.
     *
     * @throws IllegalStateException if the request is not currently PENDING
     */
    public void accept(Instant now) {
        transitionFromPending(FriendRequestStatus.ACCEPTED, now);
    }

    /**
     * Marks a PENDING request as REJECTED.
     *
     * @throws IllegalStateException if the request is not currently PENDING
     */
    public void reject(Instant now) {
        transitionFromPending(FriendRequestStatus.REJECTED, now);
    }

    private void transitionFromPending(FriendRequestStatus target, Instant now) {
        if (this.status != FriendRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "cannot " + target + " a request in status " + this.status);
        }
        this.status = target;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /** @return the normalized unordered pair of the two users involved. */
    public FriendPair pair() {
        return FriendPair.of(senderId, receiverId);
    }

    public UUID id() {
        return id;
    }

    public UUID senderId() {
        return senderId;
    }

    public UUID receiverId() {
        return receiverId;
    }

    public FriendRequestStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FriendRequest other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "FriendRequest[id=" + id
                + ", senderId=" + senderId
                + ", receiverId=" + receiverId
                + ", status=" + status + "]";
    }
}
