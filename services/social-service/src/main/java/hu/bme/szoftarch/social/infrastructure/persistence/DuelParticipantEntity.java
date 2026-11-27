package hu.bme.szoftarch.social.infrastructure.persistence;

import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code duel_participants} table (V1 baseline + V2 status
 * CHECK).
 *
 * <p>Uses a composite primary key ({@code duel_id}, {@code user_id}) declared via
 * {@link IdClass} ({@link DuelParticipantId}). The {@code version} column drives
 * optimistic locking ({@link Version}); a conflicting concurrent write surfaces as
 * an {@code OptimisticLockingFailureException}, mapped to HTTP 409
 * {@code CONCURRENT_UPDATE} higher up. All timestamps are UTC ({@link Instant}).
 */
@Entity
@Table(name = "duel_participants")
@IdClass(DuelParticipantId.class)
public class DuelParticipantEntity {

    @Id
    @Column(name = "duel_id", nullable = false, updatable = false)
    private UUID duelId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParticipantStatus status;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "answered_count", nullable = false)
    private int answeredCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DuelParticipantEntity() {
        // Required by JPA.
    }

    public DuelParticipantEntity(UUID duelId, UUID userId, ParticipantStatus status, int score,
                                 int answeredCount, Instant startedAt, Instant completedAt) {
        this.duelId = duelId;
        this.userId = userId;
        this.status = status;
        this.score = score;
        this.answeredCount = answeredCount;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public UUID getDuelId() {
        return duelId;
    }

    public void setDuelId(UUID duelId) {
        this.duelId = duelId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public void setStatus(ParticipantStatus status) {
        this.status = status;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public void setAnsweredCount(int answeredCount) {
        this.answeredCount = answeredCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
