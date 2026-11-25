package hu.bme.szoftarch.learning.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A párbaj feladatkészletének egy feladata adott pozíción (TERV 6.2
 * {@code duel_set_exercises}). A mentett készlet rögzíti a sorrendet, így
 * mindkét résztvevő pontosan ugyanazt az 5 feladatot kapja ugyanabban a
 * sorrendben (TERV 5.5).
 */
@Entity
@Table(name = "duel_set_exercises")
public class DuelSetExercise {

    @EmbeddedId
    private DuelSetExerciseId id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    protected DuelSetExercise() {
    }

    public DuelSetExercise(DuelSetExerciseId id, UUID exerciseId) {
        this.id = id;
        this.exerciseId = exerciseId;
    }

    public DuelSetExerciseId getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }
}
