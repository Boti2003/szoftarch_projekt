package hu.bme.szoftarch.social.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link DuelParticipantEntity}: ({@code duel_id},
 * {@code user_id}). Used as the {@code @IdClass} of the participant entity.
 */
public class DuelParticipantId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID duelId;
    private UUID userId;

    public DuelParticipantId() {
        // Required by JPA.
    }

    public DuelParticipantId(UUID duelId, UUID userId) {
        this.duelId = duelId;
        this.userId = userId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DuelParticipantId that)) {
            return false;
        }
        return Objects.equals(duelId, that.duelId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duelId, userId);
    }
}
