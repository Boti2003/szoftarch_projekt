package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.api.ConflictException;
import hu.bme.szoftarch.learning.api.dto.AttemptResponse;
import hu.bme.szoftarch.learning.api.dto.DailyChallengeTodayResponse;
import hu.bme.szoftarch.learning.api.dto.DuelAnswerResponse;
import hu.bme.szoftarch.learning.api.dto.DuelSetResponse;
import hu.bme.szoftarch.learning.api.dto.LessonListItemResponse;
import hu.bme.szoftarch.learning.api.dto.ScenarioCompleteResponse;
import hu.bme.szoftarch.learning.api.dto.StartScenarioSessionResponse;
import hu.bme.szoftarch.learning.application.AiScenarioService;
import hu.bme.szoftarch.learning.application.AttemptService;
import hu.bme.szoftarch.learning.application.DailyChallengeService;
import hu.bme.szoftarch.learning.application.DuelSetService;
import hu.bme.szoftarch.learning.application.EnrollmentService;
import hu.bme.szoftarch.learning.application.LessonService;
import hu.bme.szoftarch.learning.application.PointAwardService;
import hu.bme.szoftarch.learning.domain.daily.BusinessClock;
import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.LessonProgress;
import hu.bme.szoftarch.learning.domain.model.LessonProgressId;
import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.model.ProgressStatus;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgress;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgressId;
import hu.bme.szoftarch.learning.domain.port.AiEvaluationException;
import hu.bme.szoftarch.learning.domain.port.AiEvaluationPort;
import hu.bme.szoftarch.learning.domain.port.AwardPointsCommand;
import hu.bme.szoftarch.learning.domain.port.AwardPointsResult;
import hu.bme.szoftarch.learning.domain.port.IdentityPointsPort;
import hu.bme.szoftarch.learning.domain.port.ScenarioEvaluation;
import hu.bme.szoftarch.learning.domain.port.ScenarioMessage;
import hu.bme.szoftarch.learning.domain.port.ScenarioSession;
import hu.bme.szoftarch.learning.domain.port.TranslationEvaluation;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
import hu.bme.szoftarch.learning.domain.repository.LessonProgressRepository;
import hu.bme.szoftarch.learning.domain.repository.UserDailyProgressRepository;
import hu.bme.szoftarch.learning.support.ExerciseSamples;
import hu.bme.szoftarch.learning.support.TestLearningData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Valódi PostgreSQL integrációs suite a U4 viselkedés bizonyítására (FEAT-003),
 * amit a H2-s unit/MVC tesztek nem tudnak lefedni: natív JSONB payload/solution
 * oda-vissza, {@code NUMERIC(5,4)} score_ratio kötés, a 70%-os súlyozott határ
 * és a következő lecke feloldása, az idempotens újraküldés, az optimista zár
 * 409-e és a FEAT-002 parciális unique index 409-e.
 *
 * <p>Docker-t igényel (M2 gépen a hivatalos, arm64-natív {@code postgres} kép).
 * A {@code test} profil a biztonsági/JWT konfigot adja; a datasource-t és a
 * BEKAPCSOLT Flyway-t (V1 + V2 a valódi sémára) a {@link DynamicPropertySource}
 * írja felül — az {@code application-test.yml} (H2) nincs újrahasznosítva.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LearningPostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // A valódi Postgres konténerre mutatunk, és BEKAPCSOLJUK a Flyway-t, hogy
        // V1 + V2 a valódi sémát építse (JSONB, NUMERIC(5,4), parciális unique index).
        // Az application-test.yml H2 beállításait (schema.sql init, Flyway off) itt
        // felülírjuk — így az éles sémán tesztelünk.
        // A JSONB-írás natív Hibernate @JdbcTypeCode(SqlTypes.JSON) mappinggel megy
        // (nem String-kötés), ezért NINCS stringtype=unspecified kompenzáció: az URL
        // azonos az éles datasource-szal, így a teszt a valódi production-wiringet
        // bizonyítja.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private final AttemptService attemptService;
    private final AiScenarioService aiScenarioService;
    private final LessonService lessonService;
    private final EnrollmentService enrollmentService;
    private final DailyChallengeService dailyChallengeService;
    private final DuelSetService duelSetService;
    private final PointAwardService pointAwardService;
    private final ExerciseAttemptRepository attemptRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final UserDailyProgressRepository dailyProgressRepository;
    private final BusinessClock businessClock;
    private final TestLearningData data;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private IdentityPointsPort identityPointsPort;

    @MockitoBean
    private AiEvaluationPort aiEvaluationPort;

    @Autowired
    LearningPostgresIT(AttemptService attemptService, AiScenarioService aiScenarioService,
            LessonService lessonService,
            EnrollmentService enrollmentService, DailyChallengeService dailyChallengeService,
            DuelSetService duelSetService,
            PointAwardService pointAwardService, ExerciseAttemptRepository attemptRepository,
            LessonProgressRepository lessonProgressRepository,
            UserDailyProgressRepository dailyProgressRepository, BusinessClock businessClock,
            TestLearningData data, ObjectMapper objectMapper) {
        this.attemptService = attemptService;
        this.aiScenarioService = aiScenarioService;
        this.lessonService = lessonService;
        this.enrollmentService = enrollmentService;
        this.dailyChallengeService = dailyChallengeService;
        this.duelSetService = duelSetService;
        this.pointAwardService = pointAwardService;
        this.attemptRepository = attemptRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.dailyProgressRepository = dailyProgressRepository;
        this.businessClock = businessClock;
        this.data = data;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void cleanUp() {
        data.deleteAll();
    }

    // A TRUE_FALSE solution answer=false, tehát a helyes válasz: {"answer": false}.
    private static final String CORRECT_TRUE_FALSE = "{\"answer\": false}";
    private static final String WRONG_TRUE_FALSE = "{\"answer\": true}";

    private JsonNode json(String raw) {
        return objectMapper.readTree(raw);
    }

    // Egy helyes MATCHING-pár a kétpáros mintából: ratio = 1/2 = 0.5000.
    private static final String MATCHING_HALF = "{\"pairs\": [{\"leftId\": \"l1\", \"rightId\": \"r1\"}]}";
    // Egy hibás MATCHING-pár: egy elvárt pár sem helyes -> ratio = 0.0000.
    private static final String MATCHING_WRONG = "{\"pairs\": [{\"leftId\": \"l1\", \"rightId\": \"r2\"}]}";

    // (a) Natív JSONB payload/solution oda-vissza + NUMERIC(5,4) score_ratio kötés.
    @Test
    void jsonbRoundTripAndNumericScoreRatioBind() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "MATCHING", 1,
                ExerciseSamples.MATCHING.payloadJson(),
                ExerciseSamples.MATCHING.solutionJson(), 10, true);

        // A kétpáros mintából egy helyes pár -> 1/2 = 0.5 arány, ami 4 tizedesre
        // NUMERIC(5,4)-ként kötődik az adatbázisba.
        AttemptResponse response = attemptService.submit(userId, exercise, UUID.randomUUID().toString(),
                json(MATCHING_HALF));

        // NUMERIC(5,4): a visszaolvasott arány pontosan 4 tizedessel kötődik.
        ExerciseAttempt stored = attemptRepository.findByUserIdAndExerciseId(userId, exercise).getFirst();
        assertThat(stored.getScoreRatio()).isNotNull();
        assertThat(stored.getScoreRatio().scale()).isEqualTo(4);
        assertThat(stored.getScoreRatio()).isEqualByComparingTo(response.scoreRatio());
        assertThat(stored.getScoreRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));

        // JSONB oda-vissza: a tárolt payload/solution/answer JSON-struktúra épen jön vissza.
        JsonNode payload = objectMapper.readTree(
                data.jdbcString("SELECT payload::text FROM exercises WHERE id = '" + exercise + "'"));
        assertThat(payload.get("leftItems")).hasSize(2);
        assertThat(payload.get("rightItems")).hasSize(2);

        JsonNode solution = objectMapper.readTree(
                data.jdbcString("SELECT solution::text FROM exercises WHERE id = '" + exercise + "'"));
        assertThat(solution.get("pairs")).hasSize(2);
        assertThat(solution.get("pairs").get(0).get("leftId").asString()).isEqualTo("l1");

        JsonNode answer = objectMapper.readTree(
                data.jdbcString("SELECT answer::text FROM exercise_attempts WHERE id = '" + stored.getId() + "'"));
        assertThat(answer.get("pairs")).hasSize(1);
    }

    // (b) 70%-os határ átlépése -> lecke teljesül és a következő lecke feloldódik.
    @Test
    void seventyPercentBoundaryCompletesLessonAndUnlocksNext() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson1 = data.lesson(en, 1, "Lesson 1");
        UUID lesson2 = data.lesson(en, 2, "Lesson 2");
        // Két kötelező feladat egyenlő súllyal (max 10-10 pont).
        UUID ex1 = data.exerciseScorable(lesson1, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10, true);
        UUID ex2 = data.exerciseScorable(lesson1, "MATCHING", 2,
                ExerciseSamples.MATCHING.payloadJson(),
                ExerciseSamples.MATCHING.solutionJson(), 10, true);

        // ex1 helyes (1.0), ex2 részleges (0.5): súlyozott átlag = 0.75 >= 0.70 -> teljesül.
        attemptService.submit(userId, ex1, UUID.randomUUID().toString(), json(CORRECT_TRUE_FALSE));
        attemptService.submit(userId, ex2, UUID.randomUUID().toString(), json(MATCHING_HALF));

        LessonProgress progress = lessonProgressRepository
                .findById(new LessonProgressId(userId, lesson1)).orElseThrow();
        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(progress.getBestScoreRatio()).isEqualByComparingTo(new BigDecimal("0.7500"));
        assertThat(progress.getCompletedAt()).isNotNull();

        // A feloldási leképezés: a teljesített első lecke feloldja a másodikat.
        List<LessonListItemResponse> lessons = lessonService.listLessons(userId, en);
        assertThat(lessons).extracting(LessonListItemResponse::id).containsExactly(lesson1, lesson2);
        assertThat(lessons.get(0).unlocked()).isTrue();
        assertThat(lessons.get(1).unlocked()).isTrue();
    }

    // (b') 70% alatt -> a lecke NEM teljesül és a következő lecke zárva marad.
    @Test
    void belowSeventyPercentDoesNotCompleteNorUnlock() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson1 = data.lesson(en, 1, "Lesson 1");
        UUID lesson2 = data.lesson(en, 2, "Lesson 2");
        UUID ex1 = data.exerciseScorable(lesson1, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10, true);
        UUID ex2 = data.exerciseScorable(lesson1, "MATCHING", 2,
                ExerciseSamples.MATCHING.payloadJson(),
                ExerciseSamples.MATCHING.solutionJson(), 10, true);

        // ex1 helyes (1.0), ex2 helytelen (0.0): súlyozott átlag = 0.50 < 0.70 -> nem teljesül.
        attemptService.submit(userId, ex1, UUID.randomUUID().toString(), json(CORRECT_TRUE_FALSE));
        attemptService.submit(userId, ex2, UUID.randomUUID().toString(), json(MATCHING_WRONG));

        LessonProgress progress = lessonProgressRepository
                .findById(new LessonProgressId(userId, lesson1)).orElseThrow();
        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(progress.getBestScoreRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));

        List<LessonListItemResponse> lessons = lessonService.listLessons(userId, en);
        assertThat(lessons.get(1).unlocked()).isFalse();
    }

    // (c) Idempotens újraküldés valódi Postgresen: egy beküldés és azonos eredmény.
    @Test
    void idempotentReSubmitYieldsOneAttemptAndSameResult() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10, true);
        String key = UUID.randomUUID().toString();

        AttemptResponse first = attemptService.submit(userId, exercise, key, json(CORRECT_TRUE_FALSE));
        // Újraküldés eltérő válasszal, de azonos kulccsal: a tárolt eredmény jön vissza.
        AttemptResponse second = attemptService.submit(userId, exercise, key, json(WRONG_TRUE_FALSE));

        assertThat(second.attemptId()).isEqualTo(first.attemptId());
        assertThat(second.correct()).isEqualTo(first.correct());
        assertThat(second.scoreRatio()).isEqualByComparingTo(first.scoreRatio());

        List<ExerciseAttempt> attempts = attemptRepository.findByUserIdAndExerciseId(userId, exercise);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().getPointAwardStatus()).isEqualTo(PointAwardStatus.PENDING);
    }

    // (c') U4 follow-up #1: egyidejű azonos Idempotency-Key -> egy attempt, a
    // tárolt eredmény jön vissza, NINCS 500. A pre-check (findByIdempotencyKey)
    // alatt mindkét szál átcsúszhat, ezért a második INSERT a unique
    // megszorításba ütközik; ezt az AttemptService elfogja és a tárolt sort adja.
    @Test
    void concurrentSameKeySubmitYieldsOneAttemptAndNoServerError() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10, true);
        String key = UUID.randomUUID().toString();

        int threads = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        var results = java.util.Collections.synchronizedList(new java.util.ArrayList<AttemptResponse>());
        var errors = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        var done = new java.util.concurrent.CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(attemptService.submit(userId, exercise, key, json(CORRECT_TRUE_FALSE)));
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdownNow();

        // Egyetlen kivétel sincs (nincs 500-at okozó DataIntegrityViolation).
        assertThat(errors).isEmpty();
        // Pontosan egy attempt sor jött létre.
        List<ExerciseAttempt> attempts = attemptRepository.findByUserIdAndExerciseId(userId, exercise);
        assertThat(attempts).hasSize(1);
        UUID storedId = attempts.getFirst().getId();
        // Minden hívó UGYANAZT a tárolt eredményt kapta vissza.
        assertThat(results).hasSize(threads);
        assertThat(results).allSatisfy(r -> assertThat(r.attemptId()).isEqualTo(storedId));
    }

    // (d) Optimista zár: két elavult verziójú progress-írás közül a második 409.
    @Test
    void staleProgressWriteMapsToOptimisticLockConflict() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        data.progressInProgress(userId, lesson);
        LessonProgressId id = new LessonProgressId(userId, lesson);

        // Két külön, azonos (0) verziójú entitáspéldány ugyanarra a sorra.
        LessonProgress firstLoad = lessonProgressRepository.findById(id).orElseThrow();
        LessonProgress staleLoad = lessonProgressRepository.findById(id).orElseThrow();

        // Az első írás sikerül és felemeli a verziót (0 -> 1).
        firstLoad.applySubmission(new BigDecimal("0.8000"), true, java.time.Instant.now());
        lessonProgressRepository.saveAndFlush(firstLoad);

        // A második, elavult (0) verziójú írás optimista zár ütközést vált ki.
        staleLoad.applySubmission(new BigDecimal("0.9000"), true, java.time.Instant.now());
        assertThatThrownBy(() -> lessonProgressRepository.saveAndFlush(staleLoad))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // (e) Nyelvváltás (FEAT-002): a második nyelv aktiválása sikeres, és
    // fenntartja az "egy aktív nyelv / felhasználó" invariánst (a korábbi
    // aktívat deaktiválja). A parciális unique index nem sérül, mert az
    // EnrollmentService előbb deaktiválja a régit, majd aktiválja az újat.
    @Test
    void switchingActiveLanguageDeactivatesThePrevious() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID de = data.language("de", "German", true);

        enrollmentService.activateLanguage(userId, en);
        enrollmentService.activateLanguage(userId, de);

        // Pontosan egy aktív nyelv van, és az a DE.
        var response = enrollmentService.getEnrollments(userId);
        assertThat(response.activeLanguageId()).isEqualTo(de);
    }

    // ========================= U6 napi kihívás ============================

    private static final String MULTIPLE_CHOICE_CORRECT = "{\"selectedOptionIds\": [\"b\"]}";
    private static final String MATCHING_FULL_CORRECT =
            "{\"pairs\": [{\"leftId\": \"l1\", \"rightId\": \"r1\"}, {\"leftId\": \"l2\", \"rightId\": \"r2\"}]}";

    private UUID seedDailyLanguage() {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        // 3 duel-eligible auto feladat + 1 nem-duel és 1 AI, hogy a szűrés is lásson zajt.
        data.exerciseDuelEligible(lesson, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "MULTIPLE_CHOICE", 2,
                ExerciseSamples.MULTIPLE_CHOICE.payloadJson(), ExerciseSamples.MULTIPLE_CHOICE.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "MATCHING", 3,
                ExerciseSamples.MATCHING.payloadJson(), ExerciseSamples.MATCHING.solutionJson(), 10);
        // Nem duel-eligible (nem jelölt) és AI-típus duel-eligible-ként (nem auto -> kizárva).
        data.exerciseScorable(lesson, "ODD_ONE_OUT", 4,
                ExerciseSamples.ODD_ONE_OUT.payloadJson(), ExerciseSamples.ODD_ONE_OUT.solutionJson(), 10, false);
        data.exerciseDuelEligible(lesson, "TRANSLATION", 5,
                ExerciseSamples.TRANSLATION.payloadJson(), null, 10);
        return en;
    }

    private String correctAnswerFor(
            hu.bme.szoftarch.learning.domain.model.ExerciseType type) {
        return switch (type) {
            case TRUE_FALSE -> CORRECT_TRUE_FALSE;
            case MULTIPLE_CHOICE -> MULTIPLE_CHOICE_CORRECT;
            case MATCHING -> MATCHING_FULL_CORRECT;
            default -> throw new IllegalStateException("unexpected daily type " + type);
        };
    }

    // (f) Determinisztikus kiválasztás két felhasználó nézőpontból: ugyanaz a 3
    // duel-eligible auto feladat, ugyanabban a sorrendben.
    @Test
    void dailySelectionIsDeterministicAcrossUsers() {
        UUID en = seedDailyLanguage();
        UUID userOne = UUID.randomUUID();
        UUID userTwo = UUID.randomUUID();

        DailyChallengeTodayResponse first = dailyChallengeService.getToday(userOne, en);
        DailyChallengeTodayResponse second = dailyChallengeService.getToday(userTwo, en);

        assertThat(first.exercises()).hasSize(3);
        // Mind a 3 feladat auto + duel-eligible (a TRANSLATION és a nem-duel kizárva).
        assertThat(first.exercises()).allSatisfy(e -> assertThat(e.duelEligible()).isTrue());
        List<UUID> firstIds = first.exercises().stream()
                .map(hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse::exerciseId).toList();
        List<UUID> secondIds = second.exercises().stream()
                .map(hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse::exerciseId).toList();
        assertThat(secondIds).isEqualTo(firstIds);
        // Egy challenge sor jött létre a (date, languageId)-re.
        assertThat(first.challengeId()).isEqualTo(second.challengeId());
    }

    // (g) Teljesítés: 3 beküldve + >=2 helyes -> COMPLETED; <2 helyes -> nem.
    @Test
    void dailyCompletionRequiresThreeSubmittedAndAtLeastTwoCorrect() {
        UUID en = seedDailyLanguage();
        UUID userId = UUID.randomUUID();
        DailyChallengeTodayResponse today = dailyChallengeService.getToday(userId, en);

        // Két helyes, egy helytelen. A harmadik, helytelen válasz a feladat
        // TÍPUSÁHOZ illeszkedik (típus-helyes, de rossz tartalom), hogy minden
        // kiválasztási sorrendben deszerializálható legyen; a tartalom hibás, így
        // a feladat 0.0-ra értékelődik (<2 helyes nem keletkezik).
        int idx = 0;
        for (var ex : today.exercises()) {
            String answer = idx < 2 ? correctAnswerFor(ex.type())
                    : WRONG_TRUE_FALSE_OR_WRONG(ex.type());
            dailyChallengeService.submit(userId, en, ex.exerciseId(), UUID.randomUUID().toString(), json(answer));
            idx++;
        }

        UserDailyProgress progress = dailyProgressRepository
                .findById(new UserDailyProgressId(userId, today.challengeId())).orElseThrow();
        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(progress.getCorrectCount()).isGreaterThanOrEqualTo(2);
        assertThat(progress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.PENDING);

        // Egy másik user: csak egy helyes -> nem teljesül.
        UUID otherUser = UUID.randomUUID();
        for (var ex : today.exercises()) {
            String answer = ex.type() == hu.bme.szoftarch.learning.domain.model.ExerciseType.TRUE_FALSE
                    ? CORRECT_TRUE_FALSE : WRONG_TRUE_FALSE_OR_WRONG(ex.type());
            dailyChallengeService.submit(otherUser, en, ex.exerciseId(), UUID.randomUUID().toString(), json(answer));
        }
        UserDailyProgress otherProgress = dailyProgressRepository
                .findById(new UserDailyProgressId(otherUser, today.challengeId())).orElseThrow();
        assertThat(otherProgress.getStatus()).isNotEqualTo(ProgressStatus.COMPLETED);
        assertThat(otherProgress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.NOT_APPLICABLE);
    }

    private String WRONG_TRUE_FALSE_OR_WRONG(hu.bme.szoftarch.learning.domain.model.ExerciseType type) {
        return switch (type) {
            case MULTIPLE_CHOICE -> "{\"selectedOptionIds\": [\"a\"]}";
            case MATCHING -> "{\"pairs\": [{\"leftId\": \"l1\", \"rightId\": \"r2\"}]}";
            default -> WRONG_TRUE_FALSE;
        };
    }

    // (h) Párhuzamos bónusz: egyidejű bónusz-feldolgozás az optimista zár +
    // idempotenciakulcs miatt csak EGYSZER ír +20-at. A párhuzamos szálak
    // ugyanazt a determinisztikus kulcsot küldik (Identity ezzel deduplikál), és
    // a user_daily_progress-be a +20 pontosan egyszer kerül jóváírásra: az
    // optimista @Version miatt csak egy commit nyer. A bónusz az IdentityPointsPort-on megy.
    @Test
    void concurrentDailyBonusAwardsExactlyOnce() throws Exception {
        UUID en = seedDailyLanguage();
        UUID userId = UUID.randomUUID();
        Mockito.when(identityPointsPort.award(Mockito.any())).thenReturn(AwardPointsResult.AWARDED);

        DailyChallengeTodayResponse today = dailyChallengeService.getToday(userId, en);
        for (var ex : today.exercises()) {
            dailyChallengeService.submit(userId, en, ex.exerciseId(), UUID.randomUUID().toString(),
                    json(correctAnswerFor(ex.type())));
        }
        UUID challengeId = today.challengeId();
        String expectedKey = "daily:" + userId + ":" + businessClock.today() + ":bonus";

        int threads = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        var done = new java.util.concurrent.CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    pointAwardService.processDailyBonus(userId, challengeId);
                } catch (RuntimeException | InterruptedException ignored) {
                    // Optimista zár ütközés megengedett a vesztő szálon; nem dupla jóváírás.
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdownNow();

        // A párhuzamos küldések mind UGYANAZT az idempotenciakulcsot használták,
        // így az Identity deduplikál — a +20 csak egyszer számít. Az optimista zár
        // miatt a user_daily_progress-be pontosan egyszer kerül AWARDED + bonus_awarded.
        ArgumentCaptor<hu.bme.szoftarch.learning.domain.port.AwardPointsCommand> captor =
                ArgumentCaptor.forClass(hu.bme.szoftarch.learning.domain.port.AwardPointsCommand.class);
        Mockito.verify(identityPointsPort, Mockito.atLeastOnce()).award(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(c -> {
            assertThat(c.idempotencyKey()).isEqualTo(expectedKey);
            assertThat(c.points()).isEqualTo(20);
        });

        UserDailyProgress progress = dailyProgressRepository
                .findById(new UserDailyProgressId(userId, challengeId)).orElseThrow();
        assertThat(progress.isBonusAwarded()).isTrue();
        assertThat(progress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.AWARDED);
        // A verzió egyszer emelkedett a PENDING -> AWARDED átmenettel (egy nyertes commit):
        // a bónusz sor a determinisztikus kulccsal egyetlen jóváírást jelent.
        assertThat(progress.getDailyBonusAttemptCount()).isEqualTo(0);
    }

    // (i) Lazy létrehozás verseny: egyidejű első GET-ek a (date, languageId)-re ->
    // egy daily_challenges sor, nincs 500.
    @Test
    void concurrentLazyCreateYieldsOneChallengeAndNoServerError() throws Exception {
        UUID en = seedDailyLanguage();

        int threads = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        var done = new java.util.concurrent.CountDownLatch(threads);
        var results = java.util.Collections.synchronizedList(new java.util.ArrayList<UUID>());
        var errors = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(dailyChallengeService.getToday(UUID.randomUUID(), en).challengeId());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdownNow();

        assertThat(errors).isEmpty();
        // Minden hívó UGYANAZT a challenge id-t kapta (egy sor jött létre).
        assertThat(results).hasSize(threads);
        assertThat(results.stream().distinct().toList()).hasSize(1);
    }

    // (j) Budapesti üzleti nap: a challenge_date a BusinessClock által adott
    // budapesti nap, a mai GET erre a dátumra hozza létre a sort.
    @Test
    void dailyChallengeDateFollowsBudapestBusinessDay() {
        UUID en = seedDailyLanguage();
        UUID userId = UUID.randomUUID();

        DailyChallengeTodayResponse today = dailyChallengeService.getToday(userId, en);

        assertThat(today.date()).isEqualTo(businessClock.today());
        assertThat(businessClock.zone()).isEqualTo(java.time.ZoneId.of("Europe/Budapest"));
    }

    // ========================= U7 párbaj duel-set ============================

    private UUID seedDuelLanguage() {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        // 5 duel-eligible auto feladat + 1 nem-duel és 1 AI (duel-eligible, de nem
        // auto), hogy a szűrés is lásson zajt.
        data.exerciseDuelEligible(lesson, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "MULTIPLE_CHOICE", 2,
                ExerciseSamples.MULTIPLE_CHOICE.payloadJson(), ExerciseSamples.MULTIPLE_CHOICE.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "MATCHING", 3,
                ExerciseSamples.MATCHING.payloadJson(), ExerciseSamples.MATCHING.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "ODD_ONE_OUT", 4,
                ExerciseSamples.ODD_ONE_OUT.payloadJson(), ExerciseSamples.ODD_ONE_OUT.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "ERROR_FINDING", 5,
                ExerciseSamples.ERROR_FINDING.payloadJson(), ExerciseSamples.ERROR_FINDING.solutionJson(), 10);
        data.exerciseScorable(lesson, "TRUE_FALSE", 6,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10, false);
        data.exerciseDuelEligible(lesson, "TRANSLATION", 7,
                ExerciseSamples.TRANSLATION.payloadJson(), null, 10);
        return en;
    }

    private String duelCorrectAnswerFor(hu.bme.szoftarch.learning.domain.model.ExerciseType type) {
        return switch (type) {
            case TRUE_FALSE -> CORRECT_TRUE_FALSE;
            case MULTIPLE_CHOICE -> MULTIPLE_CHOICE_CORRECT;
            case MATCHING -> MATCHING_FULL_CORRECT;
            case ODD_ONE_OUT -> "{\"selectedItemId\": \"i3\"}";
            case ERROR_FINDING -> "{\"markedTokenIndexes\": [1]}";
            default -> throw new IllegalStateException("unexpected duel type " + type);
        };
    }

    private String duelWrongAnswerFor(hu.bme.szoftarch.learning.domain.model.ExerciseType type) {
        return switch (type) {
            case TRUE_FALSE -> WRONG_TRUE_FALSE;
            case MULTIPLE_CHOICE -> "{\"selectedOptionIds\": [\"a\"]}";
            case MATCHING -> "{\"pairs\": [{\"leftId\": \"l1\", \"rightId\": \"r2\"}]}";
            case ODD_ONE_OUT -> "{\"selectedItemId\": \"i1\"}";
            case ERROR_FINDING -> "{\"markedTokenIndexes\": [0]}";
            default -> throw new IllegalStateException("unexpected duel type " + type);
        };
    }

    // (k) Teljes párbaj-flow valódi Postgresen: 5 duel-eligible auto feladat
    // mentve, idempotens létrehozás, mindkét fél azonos készlet, két résztvevő
    // válaszai (helyes -> max_points, hibás -> 0), idempotens pontozás, és SOHA
    // nincs Identity pontjóváírás erről a flow-ról.
    @Test
    void duelFlowPersistsFixedSetScoresAndNeverAwardsIdentityPoints() {
        UUID en = seedDuelLanguage();
        UUID duelId = UUID.randomUUID();

        // Létrehozás: 5 feladat mentve a duel_set_exercises-be.
        DuelSetResponse created = duelSetService.createDuelSet(duelId, en, null);
        assertThat(created.exercises()).hasSize(5);
        assertThat(created.exercises()).allSatisfy(e -> assertThat(e.duelEligible()).isTrue());

        // Idempotens: ugyanarra a duelId-re ismételt létrehozás NEM hoz új készletet.
        DuelSetResponse recreated = duelSetService.createDuelSet(duelId, en, null);
        assertThat(recreated.setId()).isEqualTo(created.setId());

        // Mindkét fél (két lekérés) pontosan ugyanazt az 5 feladatot kapja
        // ugyanabban a sorrendben.
        DuelSetResponse readOne = duelSetService.getDuelSet(created.setId());
        DuelSetResponse readTwo = duelSetService.getDuelSet(created.setId());
        List<UUID> idsOne = readOne.exercises().stream()
                .map(hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse::exerciseId).toList();
        List<UUID> idsTwo = readTwo.exercises().stream()
                .map(hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse::exerciseId).toList();
        assertThat(idsTwo).isEqualTo(idsOne);

        // Két résztvevő válaszai: user1 pozíció 0 helyes -> max_points, user2
        // pozíció 1 hibás -> 0.
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        var ex0 = readOne.exercises().get(0);
        var ex1 = readOne.exercises().get(1);

        DuelAnswerResponse correct = duelSetService.submitAnswer(
                created.setId(), user1, 0, json(duelCorrectAnswerFor(ex0.type())));
        assertThat(correct.position()).isEqualTo(0);
        assertThat(correct.score()).isEqualTo(10);
        assertThat(correct.correct()).isTrue();

        DuelAnswerResponse wrong = duelSetService.submitAnswer(
                created.setId(), user2, 1, json(duelWrongAnswerFor(ex1.type())));
        assertThat(wrong.position()).isEqualTo(1);
        assertThat(wrong.score()).isEqualTo(0);
        assertThat(wrong.correct()).isFalse();

        // Idempotens pontozás: ugyanaz a (set, user, position) eltérő válasszal sem
        // pontoz újra -> az eredeti score jön vissza.
        DuelAnswerResponse repeat = duelSetService.submitAnswer(
                created.setId(), user1, 0, json(duelWrongAnswerFor(ex0.type())));
        assertThat(repeat.score()).isEqualTo(10);
        assertThat(repeat.correct()).isTrue();

        // A párbaj-flow SOHA nem kezdeményez Identity pontjóváírást.
        Mockito.verifyNoInteractions(identityPointsPort);
    }

    // (l) Idempotens létrehozás verseny valódi Postgresen: egyidejű első
    // létrehozások ugyanarra a duelId-re -> egy duel_set sor, nincs 500.
    @Test
    void concurrentDuelSetCreateYieldsOneSetAndNoServerError() throws Exception {
        UUID en = seedDuelLanguage();
        UUID duelId = UUID.randomUUID();

        int threads = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        var done = new java.util.concurrent.CountDownLatch(threads);
        var results = java.util.Collections.synchronizedList(new java.util.ArrayList<UUID>());
        var errors = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(duelSetService.createDuelSet(duelId, en, null).setId());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdownNow();

        assertThat(errors).isEmpty();
        assertThat(results).hasSize(threads);
        assertThat(results.stream().distinct().toList()).hasSize(1);
        Mockito.verifyNoInteractions(identityPointsPort);
    }

    // ========================= U8 AI kiértékelés (FEAT-004) =================

    private UUID seedTranslation(UUID lessonId, int orderIndex, boolean required) {
        return data.exerciseScorable(lessonId, "TRANSLATION", orderIndex,
                ExerciseSamples.TRANSLATION.payloadJson(), null, 10, required);
    }

    private UUID seedScenario(UUID lessonId, int orderIndex, boolean required, int maxPoints) {
        return data.exerciseScorable(lessonId, "AI_SCENARIO", orderIndex,
                ExerciseSamples.AI_SCENARIO.payloadJson(), null, maxPoints, required);
    }

    // (m) Teljes fordítós flow valódi Postgresen: >= 0.70 -> elfogadva + PENDING,
    // majd a poller a mockolt porttal AWARDED-öt kap -> AWARDED, és a parancs a
    // fix maxPoints pontot hordozza.
    @Test
    void translationAcceptedThenAwardedCarriesMaxPoints() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedTranslation(lesson, 1, true);

        Mockito.when(aiEvaluationPort.evaluateTranslation(Mockito.any()))
                .thenReturn(new TranslationEvaluation(new BigDecimal("0.85"), true, "Nice work."));
        Mockito.when(identityPointsPort.award(Mockito.any())).thenReturn(AwardPointsResult.AWARDED);

        AttemptResponse response = attemptService.submit(userId, exercise, UUID.randomUUID().toString(),
                json("{\"text\": \"Good morning\"}"));
        assertThat(response.correct()).isTrue();
        assertThat(response.pointAwardStatus()).isEqualTo(PointAwardStatus.PENDING);

        ExerciseAttempt stored = attemptRepository.findByUserIdAndExerciseId(userId, exercise).getFirst();
        assertThat(stored.getPointAwardStatus()).isEqualTo(PointAwardStatus.PENDING);
        assertThat(stored.getScoreRatio()).isEqualByComparingTo(new BigDecimal("0.8500"));

        // A poller elküldi a pontot: AWARDED, a parancs a fix maxPoints-ot hordozza.
        pointAwardService.processAttempt(stored.getId());

        ArgumentCaptor<AwardPointsCommand> captor = ArgumentCaptor.forClass(AwardPointsCommand.class);
        Mockito.verify(identityPointsPort).award(captor.capture());
        assertThat(captor.getValue().points()).isEqualTo(10);
        assertThat(captor.getValue().idempotencyKey())
                .isEqualTo("exercise:" + userId + ":" + exercise + ":first-success");

        ExerciseAttempt awarded = attemptRepository.findById(stored.getId()).orElseThrow();
        assertThat(awarded.getPointAwardStatus()).isEqualTo(PointAwardStatus.AWARDED);
    }

    // (n) Fordítós AI-hiba valódi Postgresen: a port kivételt dob -> a tranzakció
    // visszagördül, NINCS attempt sor és NINCS pontszándék, és SOHA nincs Identity-hívás.
    @Test
    void translationAiFailureWritesNoAttemptAndNoAward() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedTranslation(lesson, 1, true);

        Mockito.when(aiEvaluationPort.evaluateTranslation(Mockito.any()))
                .thenThrow(new AiEvaluationException("upstream 503"));

        assertThatThrownBy(() -> attemptService.submit(userId, exercise, UUID.randomUUID().toString(),
                json("{\"text\": \"Good morning\"}")))
                .isInstanceOf(AiEvaluationException.class);

        assertThat(attemptRepository.findByUserIdAndExerciseId(userId, exercise)).isEmpty();
        Mockito.verifyNoInteractions(identityPointsPort);
    }

    // (o) Teljes párbeszédes flow valódi Postgresen: indítás -> >= 3 üzenet ->
    // lezárás átlag >= 0.60 -> teljesült + PENDING a kiértékelt ponttal, majd a
    // poller a kiértékelt (nem maxPoints) pontot küldi.
    @Test
    void scenarioCompletedThenAwardedCarriesEvaluatedPoints() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        // maxPoints 40 -> round(0.80 * 40) = 32, 30-ra korlátozva.
        UUID exercise = seedScenario(lesson, 1, false, 40);
        UUID aiSessionId = UUID.randomUUID();

        Mockito.when(aiEvaluationPort.startScenarioSession(Mockito.any()))
                .thenReturn(new ScenarioSession(aiSessionId, "Welcome!"));
        StartScenarioSessionResponse start = aiScenarioService.startSession(
                userId, exercise, UUID.randomUUID().toString());
        assertThat(start.sessionId()).isEqualTo(aiSessionId);

        for (int turn = 1; turn <= 3; turn++) {
            Mockito.when(aiEvaluationPort.sendScenarioMessage(Mockito.any()))
                    .thenReturn(new ScenarioMessage("reply", turn));
            aiScenarioService.sendMessage(userId, aiSessionId, "hello");
        }

        Mockito.when(aiEvaluationPort.completeScenarioSession(Mockito.any()))
                .thenReturn(new ScenarioEvaluation(new BigDecimal("0.80"), new BigDecimal("0.80"),
                        new BigDecimal("0.80"), "Good conversation."));
        ScenarioCompleteResponse complete = aiScenarioService.complete(
                userId, aiSessionId, UUID.randomUUID().toString());
        assertThat(complete.completed()).isTrue();
        assertThat(complete.points()).isEqualTo(30);

        ExerciseAttempt stored = attemptRepository.findByAiSessionId(aiSessionId).orElseThrow();
        assertThat(stored.getPointAwardStatus()).isEqualTo(PointAwardStatus.PENDING);
        assertThat(stored.getAwardedPoints()).isEqualTo(30);
        assertThat(stored.getScoreRatio()).isEqualByComparingTo(new BigDecimal("0.8000"));

        // A poller a KIÉRTÉKELT (30, nem a 40-es maxPoints) pontot küldi.
        Mockito.when(identityPointsPort.award(Mockito.any())).thenReturn(AwardPointsResult.AWARDED);
        pointAwardService.processAttempt(stored.getId());

        ArgumentCaptor<AwardPointsCommand> captor = ArgumentCaptor.forClass(AwardPointsCommand.class);
        Mockito.verify(identityPointsPort).award(captor.capture());
        assertThat(captor.getValue().points()).isEqualTo(30);

        ExerciseAttempt awarded = attemptRepository.findById(stored.getId()).orElseThrow();
        assertThat(awarded.getPointAwardStatus()).isEqualTo(PointAwardStatus.AWARDED);
    }

    // (p) Nyolckörös automatikus lezárási út: a 8. körnél a lezárás engedett (a
    // MIN_TURNS feltétel teljesül), az eredmény mentve valódi Postgresen.
    @Test
    void scenarioEightTurnAutoCompletePath() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 1, false, 30);
        UUID aiSessionId = UUID.randomUUID();

        Mockito.when(aiEvaluationPort.startScenarioSession(Mockito.any()))
                .thenReturn(new ScenarioSession(aiSessionId, "Welcome!"));
        aiScenarioService.startSession(userId, exercise, UUID.randomUUID().toString());

        // A 8. (AUTO_COMPLETE_TURN_LIMIT) körnél a sendMessage automatikusan lezár,
        // ezért a záró kiértékelést még a körciklus előtt stubolni kell. 0.70-es
        // részpontok átlaga >= 0.60 -> teljesült.
        Mockito.when(aiEvaluationPort.completeScenarioSession(Mockito.any()))
                .thenReturn(new ScenarioEvaluation(new BigDecimal("0.70"), new BigDecimal("0.70"),
                        new BigDecimal("0.70"), "Done."));
        for (int turn = 1; turn <= 8; turn++) {
            Mockito.when(aiEvaluationPort.sendScenarioMessage(Mockito.any()))
                    .thenReturn(new ScenarioMessage("reply", turn));
            aiScenarioService.sendMessage(userId, aiSessionId, "hello");
        }

        // A 8. kör már lezárta a munkamenetet; a kézi lezárás idempotens, a tárolt
        // eredményt adja vissza (completed=true).
        ScenarioCompleteResponse complete = aiScenarioService.complete(
                userId, aiSessionId, UUID.randomUUID().toString());
        assertThat(complete.completed()).isTrue();

        ExerciseAttempt stored = attemptRepository.findByAiSessionId(aiSessionId).orElseThrow();
        assertThat(stored.getTurnCount()).isEqualTo(8);
        assertThat(stored.getCorrect()).isTrue();
    }

    // (q) Opcionális AI feladat jelenléte/hibája NEM befolyásolja a U4
    // leckekészültséget: a készültség csak a KÖTELEZŐ auto feladatokon dől el. A
    // kötelező TRUE_FALSE helyes beküldése önmagában teljesíti a leckét, az
    // opcionális (required_for_completion=false) AI_SCENARIO feladattól függetlenül.
    @Test
    void optionalAiExerciseDoesNotAffectLessonCompletion() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID required = data.exerciseScorable(lesson, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10, true);
        // Opcionális AI feladat (not required) ugyanabban a leckében.
        seedScenario(lesson, 2, false, 30);

        // Csak a kötelező feladatra küld be helyeset: a lecke teljesül az
        // opcionális AI feladat érintése nélkül.
        attemptService.submit(userId, required, UUID.randomUUID().toString(), json(CORRECT_TRUE_FALSE));

        LessonProgress progress = lessonProgressRepository
                .findById(new LessonProgressId(userId, lesson)).orElseThrow();
        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(progress.getBestScoreRatio()).isEqualByComparingTo(new BigDecimal("1.0000"));
        // Az opcionális AI feladathoz nem ment semmilyen kiértékelés.
        Mockito.verifyNoInteractions(aiEvaluationPort);
    }
}
