package hu.bme.szoftarch.learning.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import tools.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Egy feladat-beküldés és annak kiértékelt eredménye (TERV 6.2
 * {@code exercise_attempts}). Az {@code idempotencyKey} egyedi: ugyanazzal a
 * kulccsal újraküldött beküldés nem hoz létre új sort (U4 idempotencia).
 *
 * <p>A {@code pointAwardStatus}/{@code pointIdempotencyKey} az első helyes
 * megoldás pontjóváírásának ELŐKÉSZÍTÉSE: U4 csak {@code PENDING} szándékot és
 * determinisztikus kulcsot ír; a tényleges Identity-hívást az U5 végzi.</p>
 */
@Entity
@Table(name = "exercise_attempts")
public class ExerciseAttempt {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "context", nullable = false, length = 20)
    private AttemptContext context;

    @Column(name = "context_id")
    private UUID contextId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", columnDefinition = "jsonb")
    private JsonNode answer;

    @Column(name = "score_ratio")
    private BigDecimal scoreRatio;

    @Column(name = "correct")
    private Boolean correct;

    @Column(name = "feedback")
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "point_award_status", nullable = false, length = 20)
    private PointAwardStatus pointAwardStatus;

    @Column(name = "point_idempotency_key")
    private String pointIdempotencyKey;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    protected ExerciseAttempt() {
    }

    public ExerciseAttempt(UUID id, UUID userId, UUID exerciseId, AttemptContext context, UUID contextId,
            JsonNode answer, BigDecimal scoreRatio, boolean correct, String feedback,
            PointAwardStatus pointAwardStatus, String pointIdempotencyKey, Instant submittedAt,
            String idempotencyKey) {
        this.id = id;
        this.userId = userId;
        this.exerciseId = exerciseId;
        this.context = context;
        this.contextId = contextId;
        this.answer = answer;
        this.scoreRatio = scoreRatio;
        this.correct = correct;
        this.feedback = feedback;
        this.pointAwardStatus = pointAwardStatus;
        this.pointIdempotencyKey = pointIdempotencyKey;
        this.submittedAt = submittedAt;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public AttemptContext getContext() {
        return context;
    }

    public UUID getContextId() {
        return contextId;
    }

    public JsonNode getAnswer() {
        return answer;
    }

    public BigDecimal getScoreRatio() {
        return scoreRatio;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public String getFeedback() {
        return feedback;
    }

    public PointAwardStatus getPointAwardStatus() {
        return pointAwardStatus;
    }

    public String getPointIdempotencyKey() {
        return pointIdempotencyKey;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
