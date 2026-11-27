package hu.bme.szoftarch.social.domain.duel;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain representation of a duel between a challenger and an opponent.
 *
 * <p>Carries no persistence or framework concerns (no JPA annotations, no
 * {@code org.springframework.*} imports). It holds the duel {@link DuelStatus},
 * the expiry instant, the (nullable) winner, the two participant ids, and the
 * two {@link DuelParticipant} aggregates.
 *
 * <p>Status, {@code expiresAt}, and {@code winnerId} are only ever changed
 * through the {@link DuelStateMachine}; the mutators here are package-private
 * so the state machine is the single writer of those invariants
 * (design: "{@code DuelStatus} and {@code winnerId} are only set through the
 * state machine"). Mirrors the design Data Models {@code DuelEntity}
 * ({@code status}, {@code expiresAt}, {@code winnerId}, {@code challengerId},
 * {@code opponentId}).
 */
public final class Duel {

    private final UUID id;
    private final UUID challengerId;
    private final UUID opponentId;
    private final DuelParticipant challenger;
    private final DuelParticipant opponent;
    private DuelStatus status;
    private Instant expiresAt;
    private UUID winnerId;

    /**
     * Reconstitutes a duel (e.g. when loading from storage).
     *
     * @throws NullPointerException     if any non-nullable argument is
     *                                  {@code null}
     * @throws IllegalArgumentException if challenger and opponent ids are equal
     *                                  or the participants do not match those
     *                                  ids
     */
    public Duel(UUID id, UUID challengerId, UUID opponentId, DuelStatus status,
                Instant expiresAt, UUID winnerId,
                DuelParticipant challenger, DuelParticipant opponent) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.challengerId = Objects.requireNonNull(challengerId, "challengerId must not be null");
        this.opponentId = Objects.requireNonNull(opponentId, "opponentId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.challenger = Objects.requireNonNull(challenger, "challenger must not be null");
        this.opponent = Objects.requireNonNull(opponent, "opponent must not be null");
        if (challengerId.equals(opponentId)) {
            throw new IllegalArgumentException("challenger and opponent must differ");
        }
        if (!challenger.userId().equals(challengerId)) {
            throw new IllegalArgumentException("challenger participant id must equal challengerId");
        }
        if (!opponent.userId().equals(opponentId)) {
            throw new IllegalArgumentException("opponent participant id must equal opponentId");
        }
        this.winnerId = winnerId;
    }

    /**
     * Returns the participant for the given user id.
     *
     * @param userId the acting user id (JWT {@code sub})
     * @return the matching {@link DuelParticipant}, or {@code null} if the user
     *         is neither the challenger nor the opponent
     */
    public DuelParticipant participantOf(UUID userId) {
        if (challengerId.equals(userId)) {
            return challenger;
        }
        if (opponentId.equals(userId)) {
            return opponent;
        }
        return null;
    }

    /** @return {@code true} iff the user is the challenger or the opponent. */
    public boolean isParticipant(UUID userId) {
        return challengerId.equals(userId) || opponentId.equals(userId);
    }

    /** @return {@code true} iff both participants are COMPLETED. */
    public boolean bothParticipantsCompleted() {
        return challenger.status() == ParticipantStatus.COMPLETED
                && opponent.status() == ParticipantStatus.COMPLETED;
    }

    // --- package-private mutators: only the state machine writes these ---

    void setStatus(DuelStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    void setExpiresAt(Instant expiresAt) {
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    void setWinnerId(UUID winnerId) {
        this.winnerId = winnerId;
    }

    DuelParticipant challenger() {
        return challenger;
    }

    DuelParticipant opponent() {
        return opponent;
    }

    public UUID id() {
        return id;
    }

    public UUID challengerId() {
        return challengerId;
    }

    public UUID opponentId() {
        return opponentId;
    }

    public DuelStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public UUID winnerId() {
        return winnerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Duel other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Duel[id=" + id
                + ", challengerId=" + challengerId
                + ", opponentId=" + opponentId
                + ", status=" + status
                + ", winnerId=" + winnerId + "]";
    }
}
