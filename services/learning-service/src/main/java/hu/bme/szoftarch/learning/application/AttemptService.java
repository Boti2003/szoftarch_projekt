package hu.bme.szoftarch.learning.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import hu.bme.szoftarch.learning.api.NotFoundException;
import hu.bme.szoftarch.learning.api.dto.AttemptResponse;
import hu.bme.szoftarch.learning.domain.exercise.EvaluationResult;
import hu.bme.szoftarch.learning.domain.exercise.EvaluatorRegistry;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseSolution;
import hu.bme.szoftarch.learning.domain.exercise.UserAnswer;
import hu.bme.szoftarch.learning.domain.model.AttemptContext;
import hu.bme.szoftarch.learning.domain.model.Exercise;
import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;
import hu.bme.szoftarch.learning.domain.model.LessonProgress;
import hu.bme.szoftarch.learning.domain.model.LessonProgressId;
import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.progress.LessonCompletion;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
import hu.bme.szoftarch.learning.domain.repository.ExerciseRepository;
import hu.bme.szoftarch.learning.domain.repository.LessonProgressRepository;
import hu.bme.szoftarch.learning.infrastructure.persistence.ExerciseContentCodec;

import tools.jackson.databind.JsonNode;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feladat-beküldés use case (TERV B – U4, 5.3). Kiértékeli a felhasználó
 * válaszát az U3 {@link EvaluatorRegistry}-vel, perzisztálja a beküldést,
 * frissíti a leckehaladást a 70%-os súlyozott teljesítési szabály szerint, és
 * az első helyes megoldáshoz ELŐKÉSZÍTI a pontjóváírást.
 *
 * <p><strong>Idempotencia:</strong> azonos {@code Idempotency-Key} esetén a
 * tárolt eredményt adja vissza, új sor és új pontszándék nélkül. Egyidejű
 * azonos kulcsú beküldésnél (U4 follow-up #1) a pre-check után is ütközhet a
 * {@code exercise_attempts.idempotency_key} unique megszorítás; a vesztes
 * beküldés tranzakciója visszagördül, és a tárolt (nyertes) eredményt adjuk
 * vissza egy FRISS tranzakcióban olvasva — nem 500, nem dupla sor/pontszándék.</p>
 *
 * <p><strong>Kritikus (TERV 5.2):</strong> a helyes megoldás sem a válaszban,
 * sem logban nem jelenik meg; a {@code userId} kizárólag a JWT {@code sub}
 * claimjéből jön (a hívó adja át), soha nem request bodyból.</p>
 */
@Service
public class AttemptService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseAttemptRepository attemptRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final ExerciseContentCodec contentCodec;
    private final EvaluatorRegistry evaluatorRegistry;
    private final AttemptService self;

    public AttemptService(ExerciseRepository exerciseRepository,
            ExerciseAttemptRepository attemptRepository,
            LessonProgressRepository lessonProgressRepository,
            ExerciseContentCodec contentCodec,
            EvaluatorRegistry evaluatorRegistry,
            @Lazy AttemptService self) {
        this.exerciseRepository = exerciseRepository;
        this.attemptRepository = attemptRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.contentCodec = contentCodec;
        this.evaluatorRegistry = evaluatorRegistry;
        this.self = self;
    }

    /**
     * Beküld egy választ egy feladatra, kiértékeli és elmenti. Orkesztrátor: a
     * tényleges DB-munka saját tranzakcióban fut ({@link #submitInTransaction});
     * egyidejű azonos kulcsnál a visszagördülő tranzakció után a tárolt eredményt
     * egy külön olvasó tranzakcióban adja vissza.
     *
     * @param userId         a hitelesített felhasználó (JWT {@code sub})
     * @param exerciseId     a feladat azonosítója
     * @param idempotencyKey a beküldés idempotencia-kulcsa
     * @param answerJson     a nyers válasz JSON
     * @return a kiértékelt eredmény (megoldás nélkül)
     */
    public AttemptResponse submit(UUID userId, UUID exerciseId, String idempotencyKey, JsonNode answerJson) {
        try {
            return self.submitInTransaction(userId, exerciseId, idempotencyKey, answerJson);
        } catch (DataIntegrityViolationException e) {
            // Egyidejű azonos Idempotency-Key: a vesztő tranzakció visszagördült.
            // A nyertes által commitolt sort friss tranzakcióban olvassuk vissza.
            return self.findStoredResult(idempotencyKey)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * A beküldés DB-munkája egyetlen tranzakcióban. Unique ütközésnél a
     * {@link DataIntegrityViolationException} kibukik (a tranzakció visszagördül),
     * és a hívó {@link #submit} a tárolt eredménnyel válaszol.
     */
    @Transactional
    public AttemptResponse submitInTransaction(UUID userId, UUID exerciseId, String idempotencyKey,
            JsonNode answerJson) {
        // (a) Idempotencia: azonos kulcsra a tárolt eredmény, új sor/pontszándék nélkül.
        var existing = attemptRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // (b) Feladat betöltése; nem létező/letiltott -> 404.
        Exercise exercise = exerciseRepository.findByIdAndEnabledTrue(exerciseId)
                .orElseThrow(() -> new NotFoundException(
                        "exercise_not_found", "Exercise not found or not available"));

        // (c) AI-típusok nem itt értékelődnek; kezelt 400 a handlerben.
        ExerciseType type = exercise.getType();
        // (d) Megbízható megoldás + kliensválasz feloldása, majd kiértékelés.
        ExerciseSolution solution = contentCodec.readSolution(type, exercise.getSolution())
                .orElseThrow(() -> new hu.bme.szoftarch.learning.domain.exercise.NotAutoEvaluableException(type));
        UserAnswer answer = contentCodec.readAnswer(type, answerJson);
        EvaluationResult result = evaluatorRegistry.evaluate(type, solution, answer);

        // (f) Pontszándék: csak az ELSŐ helyes megoldásra PENDING.
        boolean firstSuccess = result.correct() && attemptRepository
                .findByUserIdAndExerciseId(userId, exerciseId).stream()
                .noneMatch(a -> Boolean.TRUE.equals(a.getCorrect()));
        PointAwardStatus pointAwardStatus;
        String pointIdempotencyKey;
        if (firstSuccess) {
            pointAwardStatus = PointAwardStatus.PENDING;
            pointIdempotencyKey = "exercise:" + userId + ":" + exerciseId + ":first-success";
        } else {
            pointAwardStatus = PointAwardStatus.NOT_APPLICABLE;
            pointIdempotencyKey = null;
        }

        // (e) Beküldés perzisztálása. A saveAndFlush a tranzakción belül azonnal
        // kiváltja az esetleges unique ütközést, amit a hívó orkesztrátor kezel.
        Instant now = Instant.now();
        ExerciseAttempt attempt = new ExerciseAttempt(
                UUID.randomUUID(), userId, exerciseId, AttemptContext.LESSON, exercise.getLessonId(),
                answerJson, result.scoreRatio(), result.correct(), result.feedback(),
                pointAwardStatus, pointIdempotencyKey, now, idempotencyKey);
        attemptRepository.saveAndFlush(attempt);

        // (g) Leckehaladás frissítése az optimista zárra támaszkodva.
        updateLessonProgress(userId, exercise.getLessonId(), now);

        return toResponse(attempt);
    }

    /**
     * A tárolt beküldés eredménye az idempotencia-kulcs alapján, FRISS
     * tranzakcióban (az ütközés utáni visszaolvasáshoz). Új tranzakció kell,
     * mert a vesztő beküldés eredeti tranzakciója már visszagördült.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public java.util.Optional<AttemptResponse> findStoredResult(String idempotencyKey) {
        return attemptRepository.findByIdempotencyKey(idempotencyKey).map(AttemptService::toResponse);
    }

    /**
     * A lecke kötelező feladatainak legfrissebb legjobb pontarányaiból
     * újraszámítja a súlyozott teljesítést, és frissíti a {@link LessonProgress}
     * állapotát. A frissen mentett beküldés benne van a számításban, mert a
     * repository lekérdezés a tranzakción belül látja.
     */
    private void updateLessonProgress(UUID userId, UUID lessonId, Instant now) {
        List<Exercise> required = exerciseRepository
                .findByLessonIdAndEnabledTrueOrderByOrderIndexAsc(lessonId).stream()
                .filter(Exercise::isRequiredForCompletion)
                .toList();

        List<UUID> requiredIds = required.stream().map(Exercise::getId).toList();
        Map<UUID, BigDecimal> bestRatios = attemptRepository
                .findByUserIdAndExerciseIdIn(userId, requiredIds).stream()
                .filter(a -> a.getScoreRatio() != null)
                .collect(Collectors.toMap(
                        ExerciseAttempt::getExerciseId,
                        ExerciseAttempt::getScoreRatio,
                        (a, b) -> a.compareTo(b) >= 0 ? a : b));

        List<LessonCompletion.RequiredExercise> requiredExercises = required.stream()
                .map(ex -> new LessonCompletion.RequiredExercise(ex.getId(), ex.getMaxPoints()))
                .toList();
        LessonCompletion completion = new LessonCompletion(requiredExercises, bestRatios);

        LessonProgressId progressId = new LessonProgressId(userId, lessonId);
        LessonProgress progress = lessonProgressRepository.findById(progressId)
                .orElseGet(() -> LessonProgress.start(progressId));
        progress.applySubmission(completion.weightedBestRatio(), completion.isCompleted(), now);
        lessonProgressRepository.save(progress);
    }

    private static AttemptResponse toResponse(ExerciseAttempt attempt) {
        return new AttemptResponse(
                attempt.getId(), attempt.getScoreRatio(),
                Boolean.TRUE.equals(attempt.getCorrect()), attempt.getFeedback(),
                attempt.getPointAwardStatus());
    }
}
