package hu.bme.szoftarch.learning.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import hu.bme.szoftarch.learning.api.NotFoundException;
import hu.bme.szoftarch.learning.api.dto.AttemptResponse;
import hu.bme.szoftarch.learning.api.dto.DailyChallengeTodayResponse;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse;
import hu.bme.szoftarch.learning.domain.daily.BusinessClock;
import hu.bme.szoftarch.learning.domain.daily.DailyChallengeSelection;
import hu.bme.szoftarch.learning.domain.exercise.EvaluationResult;
import hu.bme.szoftarch.learning.domain.exercise.EvaluatorRegistry;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseSolution;
import hu.bme.szoftarch.learning.domain.exercise.NotAutoEvaluableException;
import hu.bme.szoftarch.learning.domain.exercise.UserAnswer;
import hu.bme.szoftarch.learning.domain.model.AttemptContext;
import hu.bme.szoftarch.learning.domain.model.DailyChallenge;
import hu.bme.szoftarch.learning.domain.model.DailyChallengeExercise;
import hu.bme.szoftarch.learning.domain.model.DailyChallengeExerciseId;
import hu.bme.szoftarch.learning.domain.model.Exercise;
import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;
import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.model.ProgressStatus;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgress;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgressId;
import hu.bme.szoftarch.learning.domain.repository.DailyChallengeExerciseRepository;
import hu.bme.szoftarch.learning.domain.repository.DailyChallengeRepository;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
import hu.bme.szoftarch.learning.domain.repository.ExerciseRepository;
import hu.bme.szoftarch.learning.domain.repository.UserDailyProgressRepository;
import hu.bme.szoftarch.learning.infrastructure.persistence.ExerciseContentCodec;

import tools.jackson.databind.JsonNode;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Napi kihívás use case-ei (TERV B – U6, 5.4). Két fő művelet: a mai (budapesti
 * üzleti nap szerinti) feladatsor lekérése (lazy létrehozással), és egy napi
 * feladat beküldése a saját napi haladás frissítésével.
 *
 * <p><strong>Determinisztikus kiválasztás:</strong> a {@code (date, languageId)}
 * seedből az összes felhasználó UGYANAZT a 3 duel-eligible, auto feladatot kapja,
 * ugyanabban a sorrendben (domain {@link DailyChallengeSelection}).</p>
 *
 * <p><strong>Lazy létrehozás + verseny:</strong> az első lekérdezéskor jön létre
 * a {@code daily_challenges} + {@code daily_challenge_exercises} sor; a konkurens
 * létrehozást a {@code UNIQUE (challenge_date, language_id)} védi. Ütközéskor a
 * meglévő kihívást friss tranzakcióban olvassuk vissza (mint
 * {@code AttemptService.submit}), nem 500.</p>
 *
 * <p><strong>Pont-döntés (TERV 5.4, DOKUMENTÁLT):</strong> a DAILY kontextusú
 * attempt NEM ad külön profilpontot a feladatért — a profilpontot a normál
 * LESSON kontextus „első sikeres teljesítés" szabálya adja. A napi kihívás
 * egyetlen pont-forrása a +20 bónusz, amit a teljesítéskor élesítünk és az U5
 * {@code PointAwardService} küld. Ezért a napi attempt
 * {@code pointAwardStatus = NOT_APPLICABLE}, {@code pointIdempotencyKey = null}.</p>
 *
 * <p><strong>Kritikus (TERV 5.2):</strong> a helyes megoldás sem a feladatsorban,
 * sem a beküldés válaszában nem jelenik meg; a {@code userId} a JWT {@code sub}
 * claimjéből jön, nem tulajdonos nem fér idegen napi haladáshoz.</p>
 */
@Service
public class DailyChallengeService {

    private final DailyChallengeRepository challengeRepository;
    private final DailyChallengeExerciseRepository challengeExerciseRepository;
    private final UserDailyProgressRepository progressRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseAttemptRepository attemptRepository;
    private final ExerciseContentCodec contentCodec;
    private final EvaluatorRegistry evaluatorRegistry;
    private final ExerciseService exerciseService;
    private final BusinessClock businessClock;
    private final DailyChallengeService self;

    public DailyChallengeService(DailyChallengeRepository challengeRepository,
            DailyChallengeExerciseRepository challengeExerciseRepository,
            UserDailyProgressRepository progressRepository,
            ExerciseRepository exerciseRepository,
            ExerciseAttemptRepository attemptRepository,
            ExerciseContentCodec contentCodec,
            EvaluatorRegistry evaluatorRegistry,
            ExerciseService exerciseService,
            BusinessClock businessClock,
            @Lazy DailyChallengeService self) {
        this.challengeRepository = challengeRepository;
        this.challengeExerciseRepository = challengeExerciseRepository;
        this.progressRepository = progressRepository;
        this.exerciseRepository = exerciseRepository;
        this.attemptRepository = attemptRepository;
        this.contentCodec = contentCodec;
        this.evaluatorRegistry = evaluatorRegistry;
        this.exerciseService = exerciseService;
        this.businessClock = businessClock;
        this.self = self;
    }

    /**
     * A mai (budapesti üzleti nap szerinti) napi kihívás feladatsora az adott
     * nyelven, solution nélkül, + a hívó saját napi státusza. A kihívást az első
     * lekérdezéskor lazy módon létrehozza; a konkurens létrehozás versenyét a
     * unique megszorítás védi.
     *
     * @param userId     a hitelesített felhasználó (JWT {@code sub})
     * @param languageId a nyelv azonosítója
     * @return a mai feladatsor + a hívó saját státusza
     */
    public DailyChallengeTodayResponse getToday(UUID userId, UUID languageId) {
        LocalDate date = businessClock.today();
        DailyChallenge challenge = loadOrCreateChallenge(date, languageId);
        return self.buildTodayResponse(userId, challenge);
    }

    /**
     * Betölti vagy lazy módon létrehozza a {@code (date, languageId)} kihívást. A
     * létrehozás saját tranzakcióban fut; unique ütközéskor a meglévő sort friss
     * tranzakcióban olvassuk vissza (nem 500).
     */
    private DailyChallenge loadOrCreateChallenge(LocalDate date, UUID languageId) {
        Optional<DailyChallenge> existing = challengeRepository.findByChallengeDateAndLanguageId(date, languageId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return self.createChallenge(date, languageId);
        } catch (DataIntegrityViolationException e) {
            // Egyidejű lazy létrehozás: a vesztő tranzakció visszagördült. A nyertes
            // által commitolt kihívást friss tranzakcióban olvassuk vissza.
            return self.readChallenge(date, languageId).orElseThrow(() -> e);
        }
    }

    /**
     * A {@code (date, languageId)} kihívás létrehozása: a jelölt feladatokból
     * determinisztikusan kiválaszt legfeljebb 3-at, és beszúrja a challenge +
     * challenge_exercises sorokat. A flush azonnal kiváltja az esetleges unique
     * ütközést, amit a hívó kezel.
     */
    @Transactional
    public DailyChallenge createChallenge(LocalDate date, UUID languageId) {
        List<UUID> eligible = exerciseRepository.findDailyCandidatesByLanguageId(languageId).stream()
                // Csak az 5 auto-kiértékelhető típus (AI-típusok kizárva), a domain
                // kiválasztás Spring-mentességéhez az application rétegben szűrve.
                .filter(e -> evaluatorRegistry.isAutoEvaluable(e.getType()))
                .map(Exercise::getId)
                .toList();
        List<UUID> selected = DailyChallengeSelection.select(eligible, date, languageId);

        DailyChallenge challenge = new DailyChallenge(UUID.randomUUID(), date, languageId, Instant.now());
        challengeRepository.save(challenge);

        for (int position = 0; position < selected.size(); position++) {
            challengeExerciseRepository.save(new DailyChallengeExercise(
                    new DailyChallengeExerciseId(challenge.getId(), position), selected.get(position)));
        }
        challengeRepository.flush();
        challengeExerciseRepository.flush();
        return challenge;
    }

    /**
     * A tárolt kihívás friss tranzakcióban (a lazy-létrehozás versenye utáni
     * visszaolvasáshoz). Új tranzakció kell, mert a vesztő létrehozás eredeti
     * tranzakciója már visszagördült.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<DailyChallenge> readChallenge(LocalDate date, UUID languageId) {
        return challengeRepository.findByChallengeDateAndLanguageId(date, languageId);
    }

    /**
     * A feladatsor + a hívó saját státuszának összeállítása saját
     * (read-only) tranzakcióban.
     */
    @Transactional(readOnly = true)
    public DailyChallengeTodayResponse buildTodayResponse(UUID userId, DailyChallenge challenge) {
        List<DailyChallengeExercise> positions = challengeExerciseRepository
                .findByIdChallengeIdOrderByIdPositionAsc(challenge.getId());

        List<UUID> exerciseIds = positions.stream().map(DailyChallengeExercise::getExerciseId).toList();
        Map<UUID, Exercise> byId = exerciseRepository.findAllById(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));

        List<ExercisePresentationResponse> exercises = positions.stream()
                .map(DailyChallengeExercise::getExerciseId)
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(exerciseService::toPresentation)
                .toList();

        UserDailyProgress progress = progressRepository
                .findById(new UserDailyProgressId(userId, challenge.getId()))
                .orElse(null);
        DailyChallengeTodayResponse.OwnStatus ownStatus = progress == null
                ? new DailyChallengeTodayResponse.OwnStatus(ProgressStatus.NOT_STARTED, 0, false)
                : new DailyChallengeTodayResponse.OwnStatus(
                        progress.getStatus(), progress.getCorrectCount(), progress.isBonusAwarded());

        return new DailyChallengeTodayResponse(
                challenge.getId(), challenge.getChallengeDate(), challenge.getLanguageId(),
                exercises, ownStatus);
    }

    /**
     * Beküld egy választ egy mai napi feladatra, kiértékeli és frissíti a napi
     * haladást. A DB-munka saját tranzakcióban fut; egyidejű azonos
     * {@code Idempotency-Key}-nél a visszagördülő tranzakció után a tárolt
     * eredményt egy külön olvasó tranzakcióban adja vissza (mint U4).
     *
     * @param userId         a hitelesített felhasználó (JWT {@code sub})
     * @param languageId     a nyelv azonosítója (a mai kihívás feloldásához)
     * @param exerciseId     a feladat azonosítója (a mai kihívás egy feladata)
     * @param idempotencyKey a beküldés idempotencia-kulcsa
     * @param answerJson     a nyers válasz JSON
     * @return a kiértékelt eredmény (megoldás nélkül)
     */
    public AttemptResponse submit(UUID userId, UUID languageId, UUID exerciseId, String idempotencyKey,
            JsonNode answerJson) {
        LocalDate date = businessClock.today();
        DailyChallenge challenge = loadOrCreateChallenge(date, languageId);
        try {
            return self.submitInTransaction(userId, date, challenge, exerciseId, idempotencyKey, answerJson);
        } catch (DataIntegrityViolationException e) {
            return self.findStoredResult(idempotencyKey).orElseThrow(() -> e);
        }
    }

    /**
     * A napi beküldés DB-munkája egyetlen tranzakcióban. Unique ütközésnél a
     * {@link DataIntegrityViolationException} kibukik (a tranzakció visszagördül),
     * és a hívó a tárolt eredménnyel válaszol.
     */
    @Transactional
    public AttemptResponse submitInTransaction(UUID userId, LocalDate date, DailyChallenge challenge,
            UUID exerciseId, String idempotencyKey, JsonNode answerJson) {
        // (a) Idempotencia: azonos kulcsra a tárolt eredmény, új sor nélkül.
        Optional<ExerciseAttempt> existing = attemptRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // (b) A feladat a mai kihívás eleme kell legyen (különben 404).
        Set<UUID> challengeExerciseIds = challengeExerciseRepository
                .findByIdChallengeIdOrderByIdPositionAsc(challenge.getId()).stream()
                .map(DailyChallengeExercise::getExerciseId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!challengeExerciseIds.contains(exerciseId)) {
            throw new NotFoundException(
                    "daily_exercise_not_found", "Exercise is not part of today's daily challenge");
        }

        Exercise exercise = exerciseRepository.findByIdAndEnabledTrue(exerciseId)
                .orElseThrow(() -> new NotFoundException(
                        "exercise_not_found", "Exercise not found or not available"));

        // (c) Kiértékelés az U3 evaluatorral (AI-típus kezelt 400 a handlerben).
        ExerciseType type = exercise.getType();
        ExerciseSolution solution = contentCodec.readSolution(type, exercise.getSolution())
                .orElseThrow(() -> new NotAutoEvaluableException(type));
        UserAnswer answer = contentCodec.readAnswer(type, answerJson);
        EvaluationResult result = evaluatorRegistry.evaluate(type, solution, answer);

        // (d) A napi attempt perzisztálása. DÖNTÉS (TERV 5.4): a DAILY kontextusú
        // attempt NEM ad külön profilpontot a feladatért — a profilpontot a normál
        // LESSON kontextus „első sikeres teljesítés" szabálya adja. A napi kihívás
        // egyetlen pont-forrása a +20 bónusz. Ezért a napi attempt státusza
        // NOT_APPLICABLE, pontkulcs nélkül; a dupla pontfarmolás ellen az
        // idempotenciakulcsok védenek.
        Instant now = Instant.now();
        ExerciseAttempt attempt = new ExerciseAttempt(
                UUID.randomUUID(), userId, exerciseId, AttemptContext.DAILY, challenge.getId(),
                answerJson, result.scoreRatio(), result.correct(), result.feedback(),
                PointAwardStatus.NOT_APPLICABLE, null, now, idempotencyKey);
        attemptRepository.saveAndFlush(attempt);

        // (e) Napi haladás frissítése az optimista zárra támaszkodva: a frissen
        // mentett attempt benne van a tranzakción belüli újraszámításban.
        updateDailyProgress(userId, date, challenge, challengeExerciseIds, now);

        return toResponse(attempt);
    }

    /**
     * A tárolt napi beküldés eredménye az idempotencia-kulcs alapján, FRISS
     * tranzakcióban (az ütközés utáni visszaolvasáshoz).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<AttemptResponse> findStoredResult(String idempotencyKey) {
        return attemptRepository.findByIdempotencyKey(idempotencyKey).map(DailyChallengeService::toResponse);
    }

    /**
     * Újraszámolja a felhasználó napi helyes válaszainak és beküldött
     * feladatainak számát a kihívás DAILY attemptjeiből, és a domain szabály
     * szerint frissíti a napi haladást (teljesítés + bónusz-élesítés). A
     * {@code correct_count} feladatonként a LEGUTOLSÓ beküldést veszi — a napi
     * kihívás a legutóbbi válasz helyességét számolja (nem a legjobbat), így egy
     * későbbi rossz válasz felülírja a korábbi helyeset.
     */
    private void updateDailyProgress(UUID userId, LocalDate date, DailyChallenge challenge,
            Set<UUID> challengeExerciseIds, Instant now) {
        List<ExerciseAttempt> dailyAttempts = attemptRepository
                .findByUserIdAndContextAndContextId(userId, AttemptContext.DAILY, challenge.getId());

        // Feladatonként a legutolsó beküldés helyessége (submittedAt szerint).
        Map<UUID, ExerciseAttempt> latestByExercise = new java.util.HashMap<>();
        for (ExerciseAttempt a : dailyAttempts) {
            if (!challengeExerciseIds.contains(a.getExerciseId())) {
                continue;
            }
            ExerciseAttempt prev = latestByExercise.get(a.getExerciseId());
            if (prev == null || a.getSubmittedAt().isAfter(prev.getSubmittedAt())) {
                latestByExercise.put(a.getExerciseId(), a);
            }
        }

        int submittedCount = latestByExercise.size();
        int correctCount = (int) latestByExercise.values().stream()
                .filter(a -> Boolean.TRUE.equals(a.getCorrect()))
                .count();

        String bonusIdempotencyKey = "daily:" + userId + ":" + date + ":bonus";

        UserDailyProgressId progressId = new UserDailyProgressId(userId, challenge.getId());
        UserDailyProgress progress = progressRepository.findById(progressId)
                .orElseGet(() -> UserDailyProgress.start(progressId));
        progress.applyDailyResult(correctCount, submittedCount, bonusIdempotencyKey, now);
        progressRepository.save(progress);
    }

    private static AttemptResponse toResponse(ExerciseAttempt attempt) {
        return new AttemptResponse(
                attempt.getId(), attempt.getScoreRatio(),
                Boolean.TRUE.equals(attempt.getCorrect()), attempt.getFeedback(),
                attempt.getPointAwardStatus());
    }
}
