package hu.bme.szoftarch.learning.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Összetett kulcs a {@link DailyChallengeExercise} entitáshoz:
 * {@code (challenge_id, position)}. A pozíció adja a napi kihívás feladatainak
 * determinisztikus sorrendjét.
 */
@Embeddable
public class DailyChallengeExerciseId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "position", nullable = false)
    private int position;

    protected DailyChallengeExerciseId() {
    }

    public DailyChallengeExerciseId(UUID challengeId, int position) {
        this.challengeId = challengeId;
        this.position = position;
    }

    public UUID getChallengeId() {
        return challengeId;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DailyChallengeExerciseId that)) {
            return false;
        }
        return position == that.position && Objects.equals(challengeId, that.challengeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(challengeId, position);
    }
}
