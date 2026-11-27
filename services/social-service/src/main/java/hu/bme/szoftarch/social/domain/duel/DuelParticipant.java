package hu.bme.szoftarch.social.domain.duel;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain representation of one user's progress within a duel.
 *
 * <p>Carries no persistence or framework concerns (no JPA annotations, no
 * {@code org.springframework.*} imports). It tracks the per-user
 * {@link ParticipantStatus}, accumulated {@code score}, {@code answeredCount},
 * and start/completion timestamps. State transitions are driven by the
 * {@link DuelStateMachine}; scoring accumulation is applied by the application
 * layer using {@link DuelScoring}.
 *
 * <p>Mirrors the design Data Models {@code DuelParticipantEntity}
 * ({@code status}, {@code score}, {@code answeredCount}, {@code startedAt},
 * {@code completedAt}, {@code userId}). The optimistic-lock {@code version} is
 * a persistence concern and lives on the JPA entity, not here.
 */
public final class DuelParticipant {

    private final UUID userId;
    private ParticipantStatus status;
    private int score;
    private int answeredCount;
    private Instant startedAt;
    private Instant completedAt;

    /**
     * Reconstitutes a participant (e.g. when loading from storage).
     *
     * @throws NullPointerException     if {@code userId} or {@code status} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code score} or {@code answeredCount}
     *                                  is negative
     */
    public DuelParticipant(UUID userId, ParticipantStatus status, int score,
                           int answeredCount, Instant startedAt, Instant completedAt) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        if (answeredCount < 0) {
            throw new IllegalArgumentException("answeredCount must not be negative");
        }
        this.score = score;
        this.answeredCount = answeredCount;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /**
     * Creates a fresh participant in {@link ParticipantStatus#NOT_STARTED}
     * with zero score and no answers.
     *
     * @param userId the participant's user id
     * @return a new NOT_STARTED participant
     */
    public static DuelParticipant notStarted(UUID userId) {
        return new DuelParticipant(userId, ParticipantStatus.NOT_STARTED, 0, 0, null, null);
    }

    /**
     * Marks the participant as started if not already, recording
     * {@code startedAt} the first time.
     *
     * <p>Idempotent: calling it again while IN_PROGRESS leaves state unchanged
     * and does not overwrite the original {@code startedAt}. Only a
     * NOT_STARTED participant transitions to IN_PROGRESS here; the duel-level
     * legality of starting is enforced by the state machine.
     */
    void markStarted(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == ParticipantStatus.NOT_STARTED) {
            this.status = ParticipantStatus.IN_PROGRESS;
            this.startedAt = now;
        }
    }

    /**
     * Marks the participant as completed, recording {@code completedAt}.
     *
     * <p>Idempotent: completing an already-completed participant leaves the
     * original {@code completedAt} intact.
     */
    void markCompleted(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status != ParticipantStatus.COMPLETED) {
            this.status = ParticipantStatus.COMPLETED;
            this.completedAt = now;
        }
    }

    public UUID userId() {
        return userId;
    }

    public ParticipantStatus status() {
        return status;
    }

    public int score() {
        return score;
    }

    public int answeredCount() {
        return answeredCount;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DuelParticipant other)) {
            return false;
        }
        return userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }

    @Override
    public String toString() {
        return "DuelParticipant[userId=" + userId
                + ", status=" + status
                + ", score=" + score
                + ", answeredCount=" + answeredCount + "]";
    }
}
