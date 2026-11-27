package hu.bme.szoftarch.social.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link FriendshipEntity}.
 *
 * <p>Friendships are stored as an ordered pair ({@code user_low_id <
 * user_high_id}). Callers must normalize the pair (via
 * {@code hu.bme.szoftarch.social.domain.friend.FriendPair}) before invoking the
 * ordered-pair methods.
 */
public interface FriendshipRepository extends JpaRepository<FriendshipEntity, UUID> {

    /** Looks up a friendship by its normalized ordered pair. */
    Optional<FriendshipEntity> findByUserLowIdAndUserHighId(UUID userLowId, UUID userHighId);

    /** Returns {@code true} when a friendship exists for the normalized ordered pair. */
    boolean existsByUserLowIdAndUserHighId(UUID userLowId, UUID userHighId);

    /** Deletes the friendship for the normalized ordered pair, if present. */
    @Modifying
    long deleteByUserLowIdAndUserHighId(UUID userLowId, UUID userHighId);

    /**
     * Pages a user's friendships, matching the user as either the low or high
     * member of the stored ordered pair.
     */
    @Query("""
            SELECT f FROM FriendshipEntity f
            WHERE f.userLowId = :userId OR f.userHighId = :userId
            """)
    Page<FriendshipEntity> findAllForUser(@Param("userId") UUID userId, Pageable pageable);
}
