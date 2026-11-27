package hu.bme.szoftarch.social.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code friendships} table (V1 baseline).
 *
 * <p>The pair is always stored ordered ({@code user_low_id < user_high_id},
 * enforced by a DB {@code CHECK} and a {@code UNIQUE (user_low_id, user_high_id)}
 * constraint). {@code created_at} is stored in UTC ({@link Instant}).
 */
@Entity
@Table(name = "friendships")
public class FriendshipEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_low_id", nullable = false, updatable = false)
    private UUID userLowId;

    @Column(name = "user_high_id", nullable = false, updatable = false)
    private UUID userHighId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FriendshipEntity() {
        // Required by JPA.
    }

    public FriendshipEntity(UUID id, UUID userLowId, UUID userHighId, Instant createdAt) {
        this.id = id;
        this.userLowId = userLowId;
        this.userHighId = userHighId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserLowId() {
        return userLowId;
    }

    public void setUserLowId(UUID userLowId) {
        this.userLowId = userLowId;
    }

    public UUID getUserHighId() {
        return userHighId;
    }

    public void setUserHighId(UUID userHighId) {
        this.userHighId = userHighId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
