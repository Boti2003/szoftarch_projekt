package hu.bme.szoftarch.learning.domain.repository;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.DuelSetExercise;
import hu.bme.szoftarch.learning.domain.model.DuelSetExerciseId;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link DuelSetExercise} entitáshoz (U7).
 */
public interface DuelSetExerciseRepository
        extends JpaRepository<DuelSetExercise, DuelSetExerciseId> {

    /**
     * @return a párbaj feladatkészletének feladatai pozíció szerint növekvő
     *     sorrendben (a mentett, rögzített sorrend)
     */
    List<DuelSetExercise> findByIdDuelSetIdOrderByIdPositionAsc(UUID duelSetId);
}
