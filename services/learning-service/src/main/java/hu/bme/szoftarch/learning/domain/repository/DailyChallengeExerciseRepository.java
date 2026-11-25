package hu.bme.szoftarch.learning.domain.repository;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.DailyChallengeExercise;
import hu.bme.szoftarch.learning.domain.model.DailyChallengeExerciseId;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link DailyChallengeExercise} entitáshoz (U6).
 */
public interface DailyChallengeExerciseRepository
        extends JpaRepository<DailyChallengeExercise, DailyChallengeExerciseId> {

    /**
     * @return a napi kihívás feladatai pozíció szerint növekvő sorrendben (a
     *     determinisztikus sorrend)
     */
    List<DailyChallengeExercise> findByIdChallengeIdOrderByIdPositionAsc(UUID challengeId);
}
