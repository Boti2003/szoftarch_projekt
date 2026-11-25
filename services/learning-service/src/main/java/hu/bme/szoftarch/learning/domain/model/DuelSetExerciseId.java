package hu.bme.szoftarch.learning.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Összetett kulcs a {@link DuelSetExercise} entitáshoz:
 * {@code (duel_set_id, position)}. A pozíció rögzíti a párbaj feladatkészletének
 * sorrendjét; mindkét résztvevő ugyanazt az 5 feladatot kapja ugyanabban a
 * sorrendben (TERV 5.5).
 */
@Embeddable
public class DuelSetExerciseId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "duel_set_id", nullable = false)
    private UUID duelSetId;

    @Column(name = "position", nullable = false)
    private int position;

    protected DuelSetExerciseId() {
    }

    public DuelSetExerciseId(UUID duelSetId, int position) {
        this.duelSetId = duelSetId;
        this.position = position;
    }

    public UUID getDuelSetId() {
        return duelSetId;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DuelSetExerciseId that)) {
            return false;
        }
        return position == that.position && Objects.equals(duelSetId, that.duelSetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duelSetId, position);
    }
}
