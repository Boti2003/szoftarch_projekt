package hu.bme.szoftarch.learning.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import hu.bme.szoftarch.learning.api.BadRequestException;
import hu.bme.szoftarch.learning.api.ForbiddenException;
import hu.bme.szoftarch.learning.api.NotFoundException;
import hu.bme.szoftarch.learning.api.dto.ScenarioCompleteResponse;
import hu.bme.szoftarch.learning.api.dto.ScenarioMessageResponse;
import hu.bme.szoftarch.learning.api.dto.StartScenarioSessionResponse;
import hu.bme.szoftarch.learning.domain.exercise.AiScoring;
import hu.bme.szoftarch.learning.domain.exercise.ScenarioPayload;
import hu.bme.szoftarch.learning.domain.model.Exercise;
import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;
import hu.bme.szoftarch.learning.domain.port.AiEvaluationPort;
import hu.bme.szoftarch.learning.domain.port.CompleteScenarioCommand;
import hu.bme.szoftarch.learning.domain.port.ScenarioEvaluation;
import hu.bme.szoftarch.learning.domain.port.ScenarioMessage;
import hu.bme.szoftarch.learning.domain.port.ScenarioSession;
import hu.bme.szoftarch.learning.domain.port.SendMessageCommand;
import hu.bme.szoftarch.learning.domain.port.StartScenarioCommand;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
import hu.bme.szoftarch.learning.domain.repository.ExerciseRepository;
import hu.bme.szoftarch.learning.infrastructure.persistence.ExerciseContentCodec;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Párbeszédes ({@code AI_SCENARIO}) feladat use case-ei (TERV B – U8, 5.2/5.4) –
 * az U8 fő része. A learning a MEGBÍZHATÓ {@link ScenarioPayload} konfigból
 * indít egy belső AI-munkamenetet, minden üzenet a publikus learning API-n megy
 * át (a böngésző nem hamisíthat AI-eredményt/pontot), a felhasználói szöveg
 * ADATKÉNT jut a modellhez.
 *
 * <p><strong>Pont-döntés (TERV 5.2/5.4):</strong> teljesítettnek akkor számít,
 * ha a három részpont átlaga {@code >= 0.60}; az {@code AI_SCENARIO} legfeljebb
 * 30 pontot ér, a tényleges pont a kiértékelésből jön. Az első sikeres
 * teljesítéskor (userId, exerciseId) PENDING pontszándék íródik a
 * determinisztikus {@code exercise:{userId}:{exerciseId}:first-success} kulccsal
 * és a kiértékelt pontértékkel; a tényleges Identity-hívást az U5 poller végzi.
 * A learning CSAK az attempt ↔ AI-munkamenet linket és az eredményt tárolja,
 * az üzenetek tartalmát SOHA (azt az ai_db birtokolja).</p>
 *
 * <p><strong>Körkorlát (TERV):</strong> a lezárás legalább 3 kört igényel; a 8.
 * körnél a lezárás mindig engedélyezett (automatikus lezárási határ); a 8. kör
 * utáni üzenetküldést a kliens a lezárás felé terelve elutasítjuk.</p>
 *
 * <p><strong>Kritikus (TERV 5.2):</strong> a helyes megoldás sem a válaszban,
 * sem logban nem jelenik meg; a {@code userId} a JWT {@code sub} claimjéből jön,
 * nem tulajdonos nem fér idegen munkamenethez (403); a párbeszédes beküldés NEM
 * befolyásolja a lecke-teljesítést (opcionális, az U4 már szűri a
 * {@code required_for_completion} feladatokat).</p>
 */
@Service
public class AiScenarioService {

    /** A lezáráshoz szükséges minimális körszám (TERV). */
    private static final int MIN_TURNS_TO_COMPLETE = 3;

    /** Az automatikus lezárási körhatár (TERV). */
    private static final int AUTO_COMPLETE_TURN_LIMIT = 8;

    private final ExerciseRepository exerciseRepository;
    private final ExerciseAttemptRepository attemptRepository;
    private final ExerciseContentCodec contentCodec;
    private final AiEvaluationPort aiEvaluationPort;
    private final AiScenarioService self;

    public AiScenarioService(ExerciseRepository exerciseRepository,
            ExerciseAttemptRepository attemptRepository,
            ExerciseContentCodec contentCodec,
            AiEvaluationPort aiEvaluationPort,
            @Lazy AiScenarioService self) {
        this.exerciseRepository = exerciseRepository;
        this.attemptRepository = attemptRepository;
        this.contentCodec = contentCodec;
        this.aiEvaluationPort = aiEvaluationPort;
        this.self = self;
    }

    /**
     * Elindít egy párbeszédes munkamenetet egy {@code AI_SCENARIO} feladathoz:
     * létrehoz egy LESSON kontextusú beküldést a megbízható konfigból, és elindít
     * egy belső AI-munkamenetet. Orkesztrátor: a DB-munka saját tranzakcióban fut;
     * egyidejű azonos {@code Idempotency-Key} esetén a visszagördülő tranzakció
     * után a tárolt munkamenetet egy külön olvasó tranzakcióban adja vissza (mint
     * U4) – nem 500, nem dupla AI-munkamenet.
     *
     * @param userId         a hitelesített felhasználó (JWT {@code sub})
     * @param exerciseId     a feladat azonosítója
     * @param idempotencyKey a munkamenet-indítás idempotencia-kulcsa
     * @return a munkamenet azonosítója, a beküldés azonosítója és a nyitó üzenet
     */
    public StartScenarioSessionResponse startSession(UUID userId, UUID exerciseId, String idempotencyKey) {
        try {
            return self.startSessionInTransaction(userId, exerciseId, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            return self.findStoredSession(idempotencyKey).orElseThrow(() -> e);
        }
    }

    /**
     * A munkamenet-indítás DB-munkája egyetlen tranzakcióban. Unique ütközésnél a
     * {@link DataIntegrityViolationException} kibukik (a tranzakció visszagördül),
     * és a hívó a tárolt munkamenettel válaszol.
     */
    @Transactional
    public StartScenarioSessionResponse startSessionInTransaction(UUID userId, UUID exerciseId,
            String idempotencyKey) {
        // (a) Idempotencia: azonos kulcsra a tárolt munkamenet, új AI-munkamenet nélkül.
        var existing = attemptRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toStartResponse(existing.get());
        }

        // (b) Feladat betöltése; nem létező/letiltott -> 404.
        Exercise exercise = exerciseRepository.findByIdAndEnabledTrue(exerciseId)
                .orElseThrow(() -> new NotFoundException(
                        "exercise_not_found", "Exercise not found or not available"));
        if (exercise.getType() != ExerciseType.AI_SCENARIO) {
            throw new BadRequestException(
                    "exercise_not_ai_scenario", "This exercise is not an AI scenario");
        }

        // (c) Megbízható konfig feloldása és belső AI-munkamenet indítása. A
        // parancs KIZÁRÓLAG feladat-konfigot hordoz (nincs e-mail/felhasználónév/token).
        ScenarioPayload payload = (ScenarioPayload) contentCodec
                .readPayload(ExerciseType.AI_SCENARIO, exercise.getPayload());
        ScenarioSession session = aiEvaluationPort.startScenarioSession(new StartScenarioCommand(
                payload.role(), payload.situation(), payload.goals(),
                payload.targetLanguageCode(), payload.openingMessage()));

        // (d) LESSON kontextusú, üres eredményű beküldés + az AI-munkamenet linkje.
        // A saveAndFlush a tranzakción belül azonnal kiváltja az esetleges unique
        // ütközést (egyidejű azonos Idempotency-Key), amit az orkesztrátor kezel.
        ExerciseAttempt attempt = ExerciseAttempt.startScenario(
                UUID.randomUUID(), userId, exerciseId, exercise.getLessonId(),
                Instant.now(), idempotencyKey);
        attempt.assignAiSession(session.aiSessionId(), 0);
        attemptRepository.saveAndFlush(attempt);

        return new StartScenarioSessionResponse(
                session.aiSessionId(), attempt.getId(), session.openingMessage());
    }

    /**
     * A tárolt munkamenet az idempotencia-kulcs alapján, FRISS tranzakcióban (az
     * ütközés utáni visszaolvasáshoz).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public java.util.Optional<StartScenarioSessionResponse> findStoredSession(String idempotencyKey) {
        return attemptRepository.findByIdempotencyKey(idempotencyKey).map(AiScenarioService::toStartResponse);
    }

    /**
     * Elküld egy felhasználói üzenetet egy futó munkamenetbe ADATKÉNT, és
     * frissíti a tárolt körszámot. A learning NEM tárolja az üzenet szövegét (azt
     * az ai_db birtokolja).
     *
     * @param userId      a hitelesített felhasználó (JWT {@code sub})
     * @param sessionId   az AI-munkamenet azonosítója
     * @param userMessage a felhasználó üzenete
     * @return az AI válasza és a kör sorszáma
     */
    @Transactional
    public ScenarioMessageResponse sendMessage(UUID userId, UUID sessionId, String userMessage) {
        ExerciseAttempt attempt = requireOwnedAttempt(userId, sessionId);

        // Már lezárt munkamenetre nem küldünk több üzenetet: a tárolt eredményt adjuk.
        if (attempt.getCorrect() != null) {
            return new ScenarioMessageResponse(null, attempt.getTurnCount(), true, toCompleteResponse(attempt));
        }

        ScenarioMessage message = aiEvaluationPort.sendScenarioMessage(
                new SendMessageCommand(sessionId, userMessage));
        attempt.updateTurnCount(message.turnCount());
        attemptRepository.save(attempt);

        // Az automatikus lezárási határ elérésekor a kör valódi AI-választ kap,
        // majd a munkamenet rögtön lezárul és a záró értékelés is visszatér.
        if (message.turnCount() >= AUTO_COMPLETE_TURN_LIMIT) {
            ScenarioCompleteResponse result = evaluateAndComplete(userId, attempt);
            return new ScenarioMessageResponse(message.aiMessage(), message.turnCount(), true, result);
        }

        return new ScenarioMessageResponse(message.aiMessage(), message.turnCount(), false, null);
    }

    /**
     * Lezár egy párbeszédes munkamenetet: elkéri a végső kiértékelést, kiszámolja
     * a teljesítettséget és a pontot, menti az eredményt, és első sikeres
     * teljesítéskor élesíti a pontszándékot. Idempotens: a már lezárt beküldésre a
     * tárolt eredményt adja vissza, új AI-hívás nélkül.
     *
     * @param userId         a hitelesített felhasználó (JWT {@code sub})
     * @param sessionId      az AI-munkamenet azonosítója
     * @param idempotencyKey a lezárás idempotencia-kulcsa (fejléc; a tényleges
     *     idempotenciát a beküldés „már lezárt" állapota adja)
     * @return a kiértékelt eredmény (megoldás nélkül)
     */
    @Transactional
    public ScenarioCompleteResponse complete(UUID userId, UUID sessionId, String idempotencyKey) {
        ExerciseAttempt attempt = requireOwnedAttempt(userId, sessionId);

        // (a) Idempotencia: a már lezárt beküldésre a tárolt eredmény, új AI-hívás nélkül.
        if (attempt.getCorrect() != null) {
            return toCompleteResponse(attempt);
        }

        // (b) Körkorlát: legalább 3 kör kell; 8 körnél a lezárás mindig engedett.
        if (attempt.getTurnCount() < MIN_TURNS_TO_COMPLETE) {
            throw new BadRequestException("scenario_too_few_turns",
                    "The scenario needs at least " + MIN_TURNS_TO_COMPLETE + " turns to complete");
        }

        return evaluateAndComplete(userId, attempt);
    }

    /**
     * A záró kiértékelés és pontdöntés: elkéri az AI végső értékelését, kiszámolja
     * a teljesítettséget és a pontot, menti az eredményt, és első sikeres
     * teljesítéskor élesíti a pontszándékot. Közös a kézi lezárás és az
     * automatikus (körhatár) lezárás között.
     */
    private ScenarioCompleteResponse evaluateAndComplete(UUID userId, ExerciseAttempt attempt) {
        Exercise exercise = exerciseRepository.findById(attempt.getExerciseId())
                .orElseThrow(() -> new NotFoundException(
                        "exercise_not_found", "Exercise not found or not available"));

        // Végső kiértékelés az AI service-en, majd a szerveroldali pontdöntés.
        ScenarioEvaluation evaluation = aiEvaluationPort.completeScenarioSession(
                new CompleteScenarioCommand(attempt.getAiSessionId()));
        BigDecimal average = AiScoring.scenarioAverage(
                evaluation.comprehensibility(), evaluation.grammar(), evaluation.taskCompletion());
        boolean completed = AiScoring.scenarioCompleted(average);
        int points = completed ? AiScoring.scenarioAwardedPoints(average, exercise.getMaxPoints()) : 0;

        attempt.applyScenarioResult(average, completed, evaluation.feedback());

        // Pontszándék: csak az ELSŐ sikeres teljesítésre (userId, exerciseId).
        if (completed) {
            boolean firstSuccess = attemptRepository
                    .findByUserIdAndExerciseId(userId, attempt.getExerciseId()).stream()
                    .filter(a -> !a.getId().equals(attempt.getId()))
                    .noneMatch(a -> Boolean.TRUE.equals(a.getCorrect()));
            if (firstSuccess) {
                attempt.preparePointAward(
                        "exercise:" + userId + ":" + attempt.getExerciseId() + ":first-success", points);
            }
        }
        attemptRepository.save(attempt);

        return new ScenarioCompleteResponse(average, completed, points, evaluation.feedback());
    }

    /**
     * Betölti az AI-munkamenethez tartozó beküldést és ellenőrzi a tulajdonost:
     * ismeretlen munkamenet -> 404, idegen tulajdonos -> 403.
     */
    private ExerciseAttempt requireOwnedAttempt(UUID userId, UUID sessionId) {
        ExerciseAttempt attempt = attemptRepository.findByAiSessionId(sessionId)
                .orElseThrow(() -> new NotFoundException(
                        "scenario_session_not_found", "AI scenario session not found"));
        if (!attempt.getUserId().equals(userId)) {
            throw new ForbiddenException("scenario_forbidden", "This AI scenario session belongs to another user");
        }
        return attempt;
    }

    private static StartScenarioSessionResponse toStartResponse(ExerciseAttempt attempt) {
        return new StartScenarioSessionResponse(attempt.getAiSessionId(), attempt.getId(), null);
    }

    private static ScenarioCompleteResponse toCompleteResponse(ExerciseAttempt attempt) {
        boolean completed = Boolean.TRUE.equals(attempt.getCorrect());
        int points = attempt.getAwardedPoints() != null ? attempt.getAwardedPoints() : 0;
        return new ScenarioCompleteResponse(
                attempt.getScoreRatio(), completed, points, attempt.getFeedback());
    }
}
