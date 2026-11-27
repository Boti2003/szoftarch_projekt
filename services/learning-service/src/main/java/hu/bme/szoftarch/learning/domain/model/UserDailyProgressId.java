package hu.bme.szoftarch.learning.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Összetett kulcs a {@link UserDailyProgress} entitáshoz:
 * {@code (user_id, challenge_id)}. A {@code userId} külső domain-azonosító
 * (Identity); nem tulajdonos nem fér hozzá idegen napi haladáshoz.
 */
@Embeddable
public class UserDailyProgressId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    protected UserDailyProgressId() {
    }

    public UserDailyProgressId(UUID userId, UUID challengeId) {
        this.userId = userId;
        this.challengeId = challengeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getChallengeId() {
        return challengeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserDailyProgressId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(challengeId, that.challengeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, challengeId);
    }
}
