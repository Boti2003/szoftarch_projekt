package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.api.ConflictException;
import hu.bme.szoftarch.learning.api.dto.AttemptResponse;
import hu.bme.szoftarch.learning.api.dto.LessonListItemResponse;
import hu.bme.szoftarch.learning.application.AttemptService;
import hu.bme.szoftarch.learning.application.EnrollmentService;
import hu.bme.szoftarch.learning.application.LessonService;
import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.LessonProgress;
import hu.bme.szoftarch.learning.domain.model.LessonProgressId;
import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.model.ProgressStatus;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
import hu.bme.szoftarch.learning.domain.repository.LessonProgressRepository;
import hu.bme.szoftarch.learning.support.ExerciseSamples;
import hu.bme.szoftarch.learning.support.TestLearningData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
    private final LessonService lessonService;
    private final EnrollmentService enrollmentService;
    private final ExerciseAttemptRepository attemptRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final TestLearningData data;
    private final ObjectMapper objectMapper;

    @Autowired
    LearningPostgresIT(AttemptService attemptService, LessonService lessonService,
            EnrollmentService enrollmentService, ExerciseAttemptRepository attemptRepository,
            LessonProgressRepository lessonProgressRepository, TestLearningData data,
            ObjectMapper objectMapper) {
        this.attemptService = attemptService;
        this.lessonService = lessonService;
        this.enrollmentService = enrollmentService;
        this.attemptRepository = attemptRepository;
        this.lessonProgressRepository = lessonProgressRepository;
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

    // (e) Parciális unique index (FEAT-002): második aktív beiratkozás 409.
    @Test
    void secondActiveEnrollmentViolatesPartialUniqueIndexAsConflict() {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID de = data.language("de", "German", true);

        enrollmentService.activateLanguage(userId, en);

        // A felhasználónak már van aktív EN beiratkozása. A DE aktiválásakor a
        // Hibernate a flush során az ÚJ, aktív DE sort még az EN inaktiválása előtt
        // szúrja be (insert-before-update), így pillanatnyilag két aktív sor lenne a
        // felhasználóhoz -> a parciális unique index elutasítja. Az EnrollmentService
        // ezt a DataIntegrityViolationException-t ConflictException (409) válasszá fordítja.
        assertThatThrownBy(() -> enrollmentService.activateLanguage(userId, de))
                .isInstanceOf(ConflictException.class);
    }
}
