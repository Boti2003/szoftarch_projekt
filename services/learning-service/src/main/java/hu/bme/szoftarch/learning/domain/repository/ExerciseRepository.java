package hu.bme.szoftarch.learning.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.Exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * A napi kihívás jelölt feladatai (U6, TERV 5.4): egy adott nyelv elérhető,
     * duel-eligible feladatai. A nyelvet a lecke köti az adott feladathoz
     * (az {@code exercises} nem hordoz közvetlen {@code language_id}-t). Az
     * auto-típus szűrés az {@code EvaluatorRegistry}-vel az application rétegben
     * történik, hogy a domain-logika Spring-mentes maradjon.
     *
     * @param languageId a nyelv azonosítója
     * @return a jelölt feladatok azonosító szerint determinisztikusan rendezve
     */
    @Query("""
            SELECT e FROM Exercise e
            WHERE e.enabled = true
              AND e.duelEligible = true
              AND e.lessonId IN (
                  SELECT l.id FROM Lesson l WHERE l.languageId = :languageId AND l.enabled = true)
            ORDER BY e.id ASC
            """)
    List<Exercise> findDailyCandidatesByLanguageId(@Param("languageId") UUID languageId);
}
