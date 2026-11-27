package hu.bme.szoftarch.social.infrastructure.persistence;

import hu.bme.szoftarch.social.domain.friend.FriendRequestStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link FriendRequestEntity}.
 *
 * <p>Supports detecting a pending request in either direction between two users
 * and paged listing by direction (incoming = receiver, outgoing = sender) and
 * status.
 */
public interface FriendRequestRepository extends JpaRepository<FriendRequestEntity, UUID> {

    /**
     * Returns {@code true} when a {@code PENDING} request exists between the two
     * users in either direction (a→b or b→a), used to reject duplicate and
     * reverse-direction requests before relying on the DB partial-unique index.
     */
    @Query("""
            SELECT (COUNT(r) > 0) FROM FriendRequestEntity r
            WHERE r.status = hu.bme.szoftarch.social.domain.friend.FriendRequestStatus.PENDING
              AND ((r.senderId = :userA AND r.receiverId = :userB)
                OR (r.senderId = :userB AND r.receiverId = :userA))
            """)
    boolean existsPendingBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /** Pages incoming requests for a receiver, filtered by status. */
    Page<FriendRequestEntity> findByReceiverIdAndStatus(
            UUID receiverId, FriendRequestStatus status, Pageable pageable);

    /** Pages outgoing requests for a sender, filtered by status. */
    Page<FriendRequestEntity> findBySenderIdAndStatus(
            UUID senderId, FriendRequestStatus status, Pageable pageable);
}
