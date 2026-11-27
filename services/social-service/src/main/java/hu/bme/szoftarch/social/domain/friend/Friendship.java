package hu.bme.szoftarch.social.domain.friend;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain representation of a mutual friendship between two distinct users.
 *
 * <p>A friendship is stored as an ordered UUID pair where {@code low < high}
 * (design Property 1), guaranteeing a single canonical record per unordered
 * pair. This type carries no persistence or framework concerns: no JPA
 * annotations and no {@code org.springframework.*} imports.
 */
public final class Friendship {

    private final UUID id;
    private final FriendPair pair;
    private final Instant createdAt;

    /**
     * Reconstitutes a friendship from its stored ordered-pair columns.
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code userLowId >= userHighId}
     *                                  (which also rejects a self pair)
     */
    public Friendship(UUID id, UUID userLowId, UUID userHighId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.pair = new FriendPair(userLowId, userHighId);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * Creates a friendship between two distinct users, normalizing the id
     * order so that {@code low < high}.
     *
     * @throws IllegalArgumentException if {@code a.equals(b)} (self pair)
     */
    public static Friendship create(UUID id, UUID a, UUID b, Instant now) {
        FriendPair pair = FriendPair.of(a, b);
        return new Friendship(id, pair.low(), pair.high(), now);
    }

    /**
     * @param userId a candidate user id
     * @return {@code true} if {@code userId} is one of the two friends
     */
    public boolean involves(UUID userId) {
        return pair.low().equals(userId) || pair.high().equals(userId);
    }

    public UUID id() {
        return id;
    }

    public FriendPair pair() {
        return pair;
    }

    public UUID userLowId() {
        return pair.low();
    }

    public UUID userHighId() {
        return pair.high();
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Friendship other)) {
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
        return "Friendship[id=" + id
                + ", userLowId=" + userLowId()
                + ", userHighId=" + userHighId() + "]";
    }
}
