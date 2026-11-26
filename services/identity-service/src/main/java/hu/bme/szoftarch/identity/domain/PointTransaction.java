package hu.bme.szoftarch.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "point_transactions")
public class PointTransaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public PointTransaction() {
    }

    public PointTransaction(User user, String sourceType, String sourceId, int points, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.points = points;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = Instant.now();
    }

    public PointTransaction(UUID id, User user, String sourceType, String sourceId, int points, String idempotencyKey) {
        this.id = id != null ? id : UUID.randomUUID();
        this.user = user;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.points = points;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
