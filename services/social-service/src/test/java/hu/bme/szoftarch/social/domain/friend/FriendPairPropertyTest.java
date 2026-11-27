package hu.bme.szoftarch.social.domain.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for {@link FriendPair}.
 *
 * <p>Covers two design correctness properties over random UUID pairs:
 *
 * <ul>
 *   <li><b>Property 1: Ordered friendship</b> — a stored pair always has
 *       {@code low < high}, and {@code FriendPair.of(a, b)} equals
 *       {@code FriendPair.of(b, a)}.
 *   <li><b>Property 3: Self-exclusion</b> — {@code FriendPair.of(a, a)} is
 *       rejected, so no pair has {@code low == high}.
 * </ul>
 *
 * <b>Validates: Requirements 1.1, 1.3</b>
 */
class FriendPairPropertyTest {

    /**
     * Property 1 (ordering): for any two distinct ids, the normalized pair is
     * ordered so that {@code low < high} under PostgreSQL {@code uuid}
     * (unsigned big-endian 16-byte) ordering — the same ordering the DB CHECK
     * {@code chk_friendship_ordered} enforces. Deliberately not Java's signed
     * {@link UUID#compareTo}, which disagrees with the DB on ~50% of pairs.
     *
     * <b>Validates: Requirements 1.1</b>
     */
    @Property
    void ofNormalizesToLowLessThanHigh(@ForAll("uuids") UUID a, @ForAll("uuids") UUID b) {
        Assume.that(!a.equals(b));

        FriendPair pair = FriendPair.of(a, b);

        assertThat(compareUnsigned(pair.low(), pair.high())).isNegative();
    }

    /**
     * Compares two UUIDs as PostgreSQL's native {@code uuid} type does: the 16
     * bytes as an unsigned big-endian value (most-significant bits unsigned,
     * then least-significant bits unsigned).
     */
    private static int compareUnsigned(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (msb != 0) {
            return msb;
        }
        return Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    /**
     * Property 1 (symmetry): argument order does not matter —
     * {@code FriendPair.of(a, b)} equals {@code FriendPair.of(b, a)}.
     *
     * <b>Validates: Requirements 1.1</b>
     */
    @Property
    void ofIsSymmetric(@ForAll("uuids") UUID a, @ForAll("uuids") UUID b) {
        Assume.that(!a.equals(b));

        assertThat(FriendPair.of(a, b)).isEqualTo(FriendPair.of(b, a));
    }

    /**
     * Property 3: a self-pair ({@code a == b}) is rejected, so a friendship can
     * never be stored with {@code low == high}.
     *
     * <b>Validates: Requirements 1.3</b>
     */
    @Property
    void ofRejectsSelfPair(@ForAll("uuids") UUID a) {
        assertThatThrownBy(() -> FriendPair.of(a, a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("self pair");
    }

    /**
     * Random UUIDs drawn from the full 128-bit space (two random longs), which
     * exercises both orderings of {@code compareTo} rather than only the
     * cryptographically random type-4 values produced by {@link UUID#randomUUID()}.
     */
    @Provide
    Arbitrary<UUID> uuids() {
        Arbitrary<Long> longs = Arbitraries.longs();
        return Combinators.combine(longs, longs).as(UUID::new);
    }
}
