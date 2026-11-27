package hu.bme.szoftarch.learning.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import hu.bme.szoftarch.learning.api.NotFoundException;
import hu.bme.szoftarch.learning.api.dto.LessonListItemResponse;
import hu.bme.szoftarch.learning.domain.model.Language;
import hu.bme.szoftarch.learning.domain.model.Lesson;
import hu.bme.szoftarch.learning.domain.model.LessonProgress;
import hu.bme.szoftarch.learning.domain.model.ProgressStatus;
import hu.bme.szoftarch.learning.domain.repository.LanguageRepository;
import hu.bme.szoftarch.learning.domain.repository.LessonProgressRepository;
import hu.bme.szoftarch.learning.domain.repository.LessonRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leckelista use case-e (TERV B – U1). Az adott nyelv leckéit
 * {@code orderIndex} sorrendben adja vissza, mindegyikhez a bejelentkezett
 * felhasználó saját státuszával és elérhetőségével.
 *
 * <p>Feloldási szabály (olvasási leképezés): az első lecke (legkisebb
 * {@code orderIndex}) mindig elérhető; a többi akkor, ha az előző lecke az
 * adott felhasználónál {@code COMPLETED}. A tényleges feloldást az U4 írja.</p>
 */
@Service
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LanguageRepository languageRepository;

    public LessonService(LessonRepository lessonRepository,
            LessonProgressRepository lessonProgressRepository,
            LanguageRepository languageRepository) {
        this.lessonRepository = lessonRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.languageRepository = languageRepository;
    }

    /**
     * @return az adott nyelv leckéi a felhasználó státuszával és elérhetőségével
     * @throws NotFoundException ha a nyelv nem létezik vagy nem elérhető
     */
    @Transactional(readOnly = true)
    public List<LessonListItemResponse> listLessons(UUID userId, UUID languageId) {
        languageRepository.findById(languageId)
                .filter(Language::isEnabled)
                .orElseThrow(() -> new NotFoundException(
                        "language_not_found", "Language not found or not available"));

        List<Lesson> lessons = lessonRepository
                .findByLanguageIdAndEnabledTrueOrderByOrderIndexAsc(languageId);
        if (lessons.isEmpty()) {
            return List.of();
        }

        List<UUID> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Map<UUID, ProgressStatus> statusByLesson = lessonProgressRepository
                .findByIdUserIdAndIdLessonIdIn(userId, lessonIds).stream()
                .collect(Collectors.toMap(p -> p.getId().getLessonId(), LessonProgress::getStatus));

        List<LessonListItemResponse> result = new ArrayList<>(lessons.size());
        boolean previousCompleted = true; // az első lecke mindig elérhető
        for (Lesson lesson : lessons) {
            ProgressStatus status = statusByLesson.getOrDefault(lesson.getId(), ProgressStatus.NOT_STARTED);
            boolean unlocked = previousCompleted;
            result.add(new LessonListItemResponse(
                    lesson.getId(), lesson.getOrderIndex(), lesson.getTitle(), status, unlocked));
            previousCompleted = status == ProgressStatus.COMPLETED;
        }
        return result;
    }
}
