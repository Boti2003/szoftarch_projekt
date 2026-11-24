package hu.bme.szoftarch.learning.application;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.api.NotFoundException;
import hu.bme.szoftarch.learning.api.dto.ExerciseMetadataResponse;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse.ErrorFindingContent;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse.ExerciseContent;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse.MatchingContent;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse.MultipleChoiceContent;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse.OddOneOutContent;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse.ScenarioContent;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse.TranslationContent;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse.TrueFalseContent;
import hu.bme.szoftarch.learning.api.dto.LessonDetailResponse;
import hu.bme.szoftarch.learning.domain.exercise.ErrorFindingPayload;
import hu.bme.szoftarch.learning.domain.exercise.ExercisePayload;
import hu.bme.szoftarch.learning.domain.exercise.MatchingPayload;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoicePayload;
import hu.bme.szoftarch.learning.domain.exercise.OddOneOutPayload;
import hu.bme.szoftarch.learning.domain.exercise.ScenarioPayload;
import hu.bme.szoftarch.learning.domain.exercise.TranslationPayload;
import hu.bme.szoftarch.learning.domain.exercise.TrueFalsePayload;
import hu.bme.szoftarch.learning.domain.model.Exercise;
import hu.bme.szoftarch.learning.domain.model.Lesson;
import hu.bme.szoftarch.learning.domain.repository.ExerciseRepository;
import hu.bme.szoftarch.learning.domain.repository.LessonRepository;
import hu.bme.szoftarch.learning.infrastructure.persistence.ExerciseContentCodec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feladat-prezentáció use case-ei (TERV B – U2). A lecke elméletét és a
 * feladatok metaadatát, illetve egy feladat teljes prezentációját adja a
 * {@code payload} alapján.
 *
 * <p><strong>Kritikus (TERV 5.2):</strong> a kliens SOHA nem kapja meg a helyes
 * megoldást. A prezentáció kizárólag a {@code payload}-ból dolgozik; a
 * {@code solution}-t ez a réteg nem olvassa ki, és a válasz-DTO-k egyetlen ágon
 * sem tartalmaznak megoldásmezőt. A kiértékelés az U3/U4 hatóköre.</p>
 */
@Service
public class ExerciseService {

    private final LessonRepository lessonRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseContentCodec contentCodec;

    public ExerciseService(LessonRepository lessonRepository,
            ExerciseRepository exerciseRepository,
            ExerciseContentCodec contentCodec) {
        this.lessonRepository = lessonRepository;
        this.exerciseRepository = exerciseRepository;
        this.contentCodec = contentCodec;
    }

    /**
     * @return az adott lecke elmélete + feladatainak metaadata {@code orderIndex}
     *     szerint, solution nélkül
     * @throws NotFoundException ha a lecke nem létezik vagy nem elérhető
     */
    @Transactional(readOnly = true)
    public LessonDetailResponse getLessonDetail(UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .filter(Lesson::isEnabled)
                .orElseThrow(() -> new NotFoundException(
                        "lesson_not_found", "Lesson not found or not available"));

        List<ExerciseMetadataResponse> exercises = exerciseRepository
                .findByLessonIdAndEnabledTrueOrderByOrderIndexAsc(lessonId).stream()
                .map(ExerciseService::toMetadata)
                .toList();

        return new LessonDetailResponse(
                lesson.getId(), lesson.getOrderIndex(), lesson.getTitle(),
                lesson.getTheoryMarkdown(), exercises);
    }

    /**
     * @return az adott feladat teljes prezentációja a {@code payload} alapján,
     *     solution nélkül
     * @throws NotFoundException ha a feladat nem létezik vagy nem elérhető
     */
    @Transactional(readOnly = true)
    public ExercisePresentationResponse getExercisePresentation(UUID exerciseId) {
        Exercise exercise = exerciseRepository.findByIdAndEnabledTrue(exerciseId)
                .orElseThrow(() -> new NotFoundException(
                        "exercise_not_found", "Exercise not found or not available"));

        ExercisePayload payload = contentCodec.readPayload(exercise.getType(), exercise.getPayload());
        ExerciseContent content = toContent(payload);

        return new ExercisePresentationResponse(
                exercise.getId(), exercise.getType(), exercise.getDifficulty(), exercise.getPrompt(),
                exercise.getMaxPoints(), exercise.isRequiredForCompletion(), exercise.isDuelEligible(),
                content);
    }

    private static ExerciseMetadataResponse toMetadata(Exercise exercise) {
        return new ExerciseMetadataResponse(
                exercise.getId(), exercise.getType(), exercise.getDifficulty(), exercise.getPrompt(),
                exercise.getOrderIndex(), exercise.isRequiredForCompletion(), exercise.isDuelEligible(),
                exercise.getMaxPoints());
    }

    /**
     * A típusos payload recordot prezentációs tartalommá képezi. A leképezés
     * kizárólag a payload mezőit másolja; megoldásadat egyik ágon sem kerül be.
     */
    private static ExerciseContent toContent(ExercisePayload payload) {
        return switch (payload) {
            case MultipleChoicePayload p -> new MultipleChoiceContent(
                    p.multiSelect(),
                    p.options().stream()
                            .map(o -> new MultipleChoiceContent.Option(o.id(), o.text()))
                            .toList());
            case TrueFalsePayload p -> new TrueFalseContent(p.statement());
            case MatchingPayload p -> new MatchingContent(
                    p.leftItems().stream()
                            .map(i -> new MatchingContent.Item(i.id(), i.text()))
                            .toList(),
                    p.rightItems().stream()
                            .map(i -> new MatchingContent.Item(i.id(), i.text()))
                            .toList());
            case OddOneOutPayload p -> new OddOneOutContent(
                    p.items().stream()
                            .map(i -> new OddOneOutContent.Item(i.id(), i.text()))
                            .toList());
            case ErrorFindingPayload p -> new ErrorFindingContent(
                    p.tokens().stream()
                            .map(t -> new ErrorFindingContent.Token(t.index(), t.text()))
                            .toList());
            case TranslationPayload p -> new TranslationContent(
                    p.sourceText(), p.targetLanguageCode(), p.rubric());
            case ScenarioPayload p -> new ScenarioContent(
                    p.role(), p.situation(), p.goals(), p.targetLanguageCode(), p.openingMessage());
        };
    }
}
