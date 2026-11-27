package hu.bme.szoftarch.social.infrastructure.persistence;

import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code duels} table (V1 baseline + V2 status CHECK).
 *
 * <p>{@code status} is persisted as the enum name ({@link EnumType#STRING}); the
 * full enum set is enforced by the {@code chk_duels_status} constraint added in
 * V2. {@code learning_set_id}, {@code expires_at} and {@code winner_id} are
 * nullable. All timestamps are UTC ({@link Instant}).
 */
@Entity
@Table(name = "duels")
public class DuelEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "challenger_id", nullable = false, updatable = false)
    private UUID challengerId;

    @Column(name = "opponent_id", nullable = false, updatable = false)
    private UUID opponentId;

    @Column(name = "language_id", nullable = false, updatable = false)
    private UUID languageId;

    @Column(name = "learning_set_id")
    private UUID learningSetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DuelStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DuelEntity() {
        // Required by JPA.
    }

    public DuelEntity(UUID id, UUID challengerId, UUID opponentId, UUID languageId,
                      UUID learningSetId, DuelStatus status, Instant expiresAt, UUID winnerId,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.challengerId = challengerId;
        this.opponentId = opponentId;
        this.languageId = languageId;
        this.learningSetId = learningSetId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.winnerId = winnerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getChallengerId() {
        return challengerId;
    }

    public void setChallengerId(UUID challengerId) {
        this.challengerId = challengerId;
    }

    public UUID getOpponentId() {
        return opponentId;
    }

    public void setOpponentId(UUID opponentId) {
        this.opponentId = opponentId;
    }

    public UUID getLanguageId() {
        return languageId;
    }

    public void setLanguageId(UUID languageId) {
        this.languageId = languageId;
    }

    public UUID getLearningSetId() {
        return learningSetId;
    }

    public void setLearningSetId(UUID learningSetId) {
        this.learningSetId = learningSetId;
    }

    public DuelStatus getStatus() {
        return status;
    }

    public void setStatus(DuelStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(UUID winnerId) {
        this.winnerId = winnerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
