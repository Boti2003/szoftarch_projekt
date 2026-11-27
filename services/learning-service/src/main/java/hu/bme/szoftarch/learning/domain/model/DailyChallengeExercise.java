package hu.bme.szoftarch.learning.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A napi kihívás egy feladata adott pozíción (TERV 6.2
 * {@code daily_challenge_exercises}). A pozíció a determinisztikus kiválasztás
 * sorrendjét rögzíti; minden felhasználó ugyanazt a 3 feladatot kapja ugyanabban
 * a sorrendben.
 */
@Entity
@Table(name = "daily_challenge_exercises")
public class DailyChallengeExercise {

    @EmbeddedId
    private DailyChallengeExerciseId id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    protected DailyChallengeExercise() {
    }

    public DailyChallengeExercise(DailyChallengeExerciseId id, UUID exerciseId) {
        this.id = id;
        this.exerciseId = exerciseId;
    }

    public DailyChallengeExerciseId getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }
}
