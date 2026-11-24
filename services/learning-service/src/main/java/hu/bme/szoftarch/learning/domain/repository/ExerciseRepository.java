package hu.bme.szoftarch.learning.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.Exercise;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link Exercise} entitáshoz.
 */
public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    /**
     * @return az adott lecke elérhető feladatai {@code orderIndex} szerint
     *     növekvő sorrendben
     */
    List<Exercise> findByLessonIdAndEnabledTrueOrderByOrderIndexAsc(UUID lessonId);

    /**
     * @return az adott feladat, ha létezik és elérhető
     */
    Optional<Exercise> findByIdAndEnabledTrue(UUID id);
}
