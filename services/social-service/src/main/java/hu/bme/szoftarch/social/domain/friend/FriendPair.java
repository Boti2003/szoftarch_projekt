package hu.bme.szoftarch.social.domain.friend;

import java.util.Objects;
import java.util.UUID;

/**
 * Normalized, unordered representation of two distinct user ids.
 *
 * <p>A pair is always stored ordered so that {@code low < high}. This makes
 * {@code FriendPair.of(a, b)} equal to {@code FriendPair.of(b, a)} for any
 * two distinct ids, which is the basis for all friendship/uniqueness checks
 * (design Property 1: Ordered friendship).
 *
 * <p>Ordering is <b>unsigned</b> 16-byte lexicographic, matching PostgreSQL's
 * native {@code uuid} type comparison (big-endian, unsigned). This is
 * deliberately <em>not</em> {@link UUID#compareTo(UUID)}, which compares the
 * two 64-bit halves as signed longs and therefore disagrees with PostgreSQL on
 * the roughly half of all pairs that straddle a sign bit. The database CHECK
 * constraint {@code chk_friendship_ordered} ({@code user_low_id < user_high_id})
 * uses the PostgreSQL {@code uuid} ordering, so that ordering is the source of
 * truth and the domain is aligned to it.
 *
 * <p>A self-pair ({@code a == b}) is rejected (design Property 3:
 * Self-exclusion).
 */
public record FriendPair(UUID low, UUID high) {

    public FriendPair {
        Objects.requireNonNull(low, "low must not be null");
        Objects.requireNonNull(high, "high must not be null");
        if (compareUnsigned(low, high) >= 0) {
            throw new IllegalArgumentException("FriendPair must be ordered low < high");
        }
    }

    /**
     * Builds the normalized pair of two distinct user ids, ordering them so
     * that {@code low < high} under PostgreSQL {@code uuid} (unsigned
     * big-endian 16-byte) ordering.
     *
     * @param a one user id
     * @param b the other user id
     * @return the ordered pair {@code (low, high)}
     * @throws NullPointerException     if either id is {@code null}
     * @throws IllegalArgumentException if {@code a.equals(b)} (self pair)
     */
    public static FriendPair of(UUID a, UUID b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");
        if (a.equals(b)) {
            throw new IllegalArgumentException("self pair");
        }
        return (compareUnsigned(a, b) < 0) ? new FriendPair(a, b) : new FriendPair(b, a);
    }

    /**
     * Compares two UUIDs as PostgreSQL's native {@code uuid} type does: the 16
     * bytes are treated as an unsigned big-endian value. For the standard UUID
     * byte layout this is equivalent to comparing the most-significant 64 bits
     * unsigned, then the least-significant 64 bits unsigned.
     */
    private static int compareUnsigned(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (msb != 0) {
            return msb;
        }
        return Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
