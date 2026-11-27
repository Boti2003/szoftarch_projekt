package hu.bme.szoftarch.learning.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Összetett kulcs a {@link DuelAnswer} entitáshoz:
 * {@code (duel_set_id, user_id, position)}. Ez a hármas biztosítja a párbaj-
 * válasz idempotenciáját: egy résztvevő egy pozícióra pontosan egy választ
 * rögzíthet (TERV 5.5).
 */
@Embeddable
public class DuelAnswerId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "duel_set_id", nullable = false)
    private UUID duelSetId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "position", nullable = false)
    private int position;

    protected DuelAnswerId() {
    }

    public DuelAnswerId(UUID duelSetId, UUID userId, int position) {
        this.duelSetId = duelSetId;
        this.userId = userId;
        this.position = position;
    }

    public UUID getDuelSetId() {
        return duelSetId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DuelAnswerId that)) {
            return false;
        }
        return position == that.position
                && Objects.equals(duelSetId, that.duelSetId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duelSetId, userId, position);
    }
}
