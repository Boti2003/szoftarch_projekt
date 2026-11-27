package hu.bme.szoftarch.learning.domain.repository;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.LessonProgress;
import hu.bme.szoftarch.learning.domain.model.LessonProgressId;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link LessonProgress} entitáshoz. U1-ben csak
 * olvasásra használjuk; a lekérdezések a hitelesített {@code userId}-re
 * szűrnek.
 */
public interface LessonProgressRepository extends JpaRepository<LessonProgress, LessonProgressId> {

    List<LessonProgress> findByIdUserIdAndIdLessonIdIn(UUID userId, List<UUID> lessonIds);
}
