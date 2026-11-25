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

    @Column(name = "point_attempt_count", nullable = false)
    private int pointAttemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "ai_session_id")
    private UUID aiSessionId;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @Column(name = "awarded_points")
    private Integer awardedPoints;

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
        this.pointAttemptCount = 0;
        this.turnCount = 0;
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

    public int getPointAttemptCount() {
        return pointAttemptCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public UUID getAiSessionId() {
        return aiSessionId;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public Integer getAwardedPoints() {
        return awardedPoints;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * A pontjóváírás sikerült: {@code AWARDED} állapot, nincs több retry.
     */
    public void markAwarded() {
        this.pointAwardStatus = PointAwardStatus.AWARDED;
        this.nextRetryAt = null;
    }

    /**
     * Újrapróbálható hiba (5xx / timeout / hálózat, TERV 7.8): a próbálkozások
     * számát növeli, az állapotot {@code FAILED_RETRYABLE}-re állítja és beállítja
     * a következő próbálkozás idejét. A {@code nextRetryAt == null} (a 3. hiba
     * után) azt jelenti, hogy a poller a {@code next_retry_at <= now} feltétellel
     * már nem választja ki — így az automatikus retry korlátozott.
     */
    public void markRetryable(Instant nextRetryAt) {
        this.pointAttemptCount++;
        this.pointAwardStatus = PointAwardStatus.FAILED_RETRYABLE;
        this.nextRetryAt = nextRetryAt;
    }

    /**
     * Nem újrapróbálható hiba (4xx, pl. validáció): {@code FAILED_RETRYABLE}
     * állapot {@code nextRetryAt == null}-lal, így a poller nem próbálkozik újra.
     */
    public void markNonRetryable() {
        this.pointAttemptCount++;
        this.pointAwardStatus = PointAwardStatus.FAILED_RETRYABLE;
        this.nextRetryAt = null;
    }

    /**
     * Létrehoz egy párbeszédes ({@code AI_SCENARIO}) feladat LESSON kontextusú
     * induló beküldését (U8): az eredménymezők ({@code scoreRatio}, {@code correct},
     * {@code feedback}) még üresek, a pontjóváírás {@code NOT_APPLICABLE} — a
     * kiértékelés és az esetleges pontszándék a munkamenet lezárásakor dől el.
     *
     * @param id             a beküldés azonosítója
     * @param userId         a hitelesített felhasználó (JWT {@code sub})
     * @param exerciseId     a feladat azonosítója
     * @param lessonId       a lecke azonosítója (LESSON kontextus-azonosító)
     * @param submittedAt    a létrehozás pillanata
     * @param idempotencyKey a beküldés idempotencia-kulcsa
     * @return az üres eredményű, induló beküldés
     */
    public static ExerciseAttempt startScenario(UUID id, UUID userId, UUID exerciseId, UUID lessonId,
            Instant submittedAt, String idempotencyKey) {
        ExerciseAttempt attempt = new ExerciseAttempt();
        attempt.id = id;
        attempt.userId = userId;
        attempt.exerciseId = exerciseId;
        attempt.context = AttemptContext.LESSON;
        attempt.contextId = lessonId;
        attempt.answer = null;
        attempt.scoreRatio = null;
        attempt.correct = null;
        attempt.feedback = null;
        attempt.pointAwardStatus = PointAwardStatus.NOT_APPLICABLE;
        attempt.pointIdempotencyKey = null;
        attempt.pointAttemptCount = 0;
        attempt.turnCount = 0;
        attempt.submittedAt = submittedAt;
        attempt.idempotencyKey = idempotencyKey;
        return attempt;
    }

    /**
     * Beírja a párbeszédes munkamenet lezárásának kiértékelt eredményét (U8):
     * az átlagpontarányt, a teljesítettséget és a visszajelzést. A
     * {@code feedback} SOHA nem tartalmazhat megoldást (TERV 5.2).
     *
     * @param scoreRatio a három részpont átlaga a {@code [0, 1]} intervallumban
     * @param correct    igaz, ha az átlag eléri a teljesítési küszöböt
     * @param feedback   megoldást nem szivárogtató visszajelzés
     */
    public void applyScenarioResult(BigDecimal scoreRatio, boolean correct, String feedback) {
        this.scoreRatio = scoreRatio;
        this.correct = correct;
        this.feedback = feedback;
    }

    /**
     * Előkészíti az első sikeres párbeszéd-teljesítés pontjóváírását (U8): a
     * státuszt {@code PENDING}-re, a determinisztikus idempotenciakulcsot és a
     * szerveroldalon kiértékelt pontot állítja be. A tényleges Identity-hívást az
     * U5 poller végzi, ezt a kiértékelt pontot küldve.
     *
     * @param pointIdempotencyKey a determinisztikus pont-idempotenciakulcs
     * @param awardedPoints       a kiértékelt, jóváírandó pont
     */
    public void preparePointAward(String pointIdempotencyKey, int awardedPoints) {
        this.pointAwardStatus = PointAwardStatus.PENDING;
        this.pointIdempotencyKey = pointIdempotencyKey;
        this.awardedPoints = awardedPoints;
    }

    /**
     * Hozzárendeli az AI-oldali munkamenetet és beállítja a kör-számlálót
     * (U8 párbeszédes feladat indítása).
     *
     * @param aiSessionId az AI-munkamenet azonosítója
     * @param turnCount   a munkamenet induló körszáma
     */
    public void assignAiSession(UUID aiSessionId, int turnCount) {
        this.aiSessionId = aiSessionId;
        this.turnCount = turnCount;
    }

    /**
     * Frissíti a párbeszéd kör-számlálóját (U8 üzenetváltás után).
     *
     * @param turnCount az új körszám
     */
    public void updateTurnCount(int turnCount) {
        this.turnCount = turnCount;
    }

    /**
     * Beállítja a szerveroldalon kiértékelt, jóváírandó pontot (U8). Ennek
     * megléte esetén a pontjóváírás ezt küldi a fix {@code maxPoints} helyett.
     *
     * @param awardedPoints a kiértékelt pont (vagy {@code null}, ha nincs)
     */
    public void setAwardedPoints(Integer awardedPoints) {
        this.awardedPoints = awardedPoints;
    }
}
