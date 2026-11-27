package hu.bme.szoftarch.learning.domain.repository;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.Lesson;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link Lesson} entitáshoz.
 */
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    /**
     * @return az adott nyelv elérhető leckéi {@code orderIndex} szerint növekvő
     *     sorrendben
     */
    List<Lesson> findByLanguageIdAndEnabledTrueOrderByOrderIndexAsc(UUID languageId);
}
