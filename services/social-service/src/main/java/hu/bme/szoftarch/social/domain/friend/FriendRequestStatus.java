package hu.bme.szoftarch.social.domain.friend;

/**
 * Lifecycle status of a {@link FriendRequest}.
 *
 * <p>A request starts {@link #PENDING}; the receiver may move it to
 * {@link #ACCEPTED} (creating a {@link Friendship}) or {@link #REJECTED}.
 */
public enum FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
