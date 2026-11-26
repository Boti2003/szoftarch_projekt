package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;
import java.util.stream.Collectors;

import hu.bme.szoftarch.learning.domain.exercise.ErrorFindingAnswer;
import hu.bme.szoftarch.learning.domain.exercise.ErrorFindingSolution;
import hu.bme.szoftarch.learning.domain.exercise.EvaluationResult;
import hu.bme.szoftarch.learning.domain.exercise.EvaluatorRegistry;
import hu.bme.szoftarch.learning.domain.exercise.ExercisePayload;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseScoring;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseSolution;
import hu.bme.szoftarch.learning.domain.exercise.MatchingAnswer;
import hu.bme.szoftarch.learning.domain.exercise.MatchingSolution;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoiceAnswer;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoiceSolution;
import hu.bme.szoftarch.learning.domain.exercise.OddOneOutAnswer;
import hu.bme.szoftarch.learning.domain.exercise.OddOneOutSolution;
import hu.bme.szoftarch.learning.domain.exercise.TrueFalseAnswer;
import hu.bme.szoftarch.learning.domain.exercise.TrueFalseSolution;
import hu.bme.szoftarch.learning.domain.exercise.UserAnswer;
import hu.bme.szoftarch.learning.domain.daily.DailyChallengeSelection;
import hu.bme.szoftarch.learning.domain.model.Difficulty;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;
import hu.bme.szoftarch.learning.domain.port.AiEvaluationPort;
import hu.bme.szoftarch.learning.domain.port.IdentityPointsPort;
import hu.bme.szoftarch.learning.infrastructure.persistence.ExerciseContentCodec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * U9 seed migrációs és konzisztencia bizonyíték valódi PostgreSQL-en
 * (Testcontainers, M2 gépen natív arm64 {@code postgres} kép). A V1..V6
 * migráció tiszta adatbázison lefut, és a {@code V6__seed.sql} betölti a
 * tananyagot. A suite igazolja:
 *
 * <ul>
 *   <li>darabszám-minimumok: 1 nyelv, 4 lecke, leckénként &ge;5 auto + &ge;2 AI,
 *       &ge;12 duel_eligible auto összesen, minden auto típusból &ge;2;</li>
 *   <li>konzisztencia: minden seedelt auto feladat payload/solution-je
 *       deszerializálható az {@link ExerciseContentCodec}-kel, és az U3
 *       evaluator a solution szerinti helyes válaszra {@code correct=true},
 *       {@code score_ratio=1.0} eredményt ad;</li>
 *   <li>max_points = floor(alappont &times; szorzó) minden auto feladatra; az
 *       AI-típusok rögzített maxpontja TRANSLATION=15, AI_SCENARIO=30.</li>
 * </ul>
 *
 * <p>A Flyway-t a {@link DynamicPropertySource} kapcsolja be (az
 * {@code application-test.yml} H2 beállításait felülírva), hogy a valódi V1..V6
 * séma épüljön — így a seed az éles production-wiringgel bizonyítottan betölt.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SeedMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static final Set<String> AUTO_TYPES =
            Set.of("MULTIPLE_CHOICE", "TRUE_FALSE", "MATCHING", "ODD_ONE_OUT", "ERROR_FINDING");
    private static final Set<String> AI_TYPES = Set.of("TRANSLATION", "AI_SCENARIO");

    // A seed nyelvek fix UUID-jai (V6 angol, V7 német).
    private static final String EN_LANGUAGE_ID = "00000000-0000-0000-0000-0000000000e0";
    private static final String DE_LANGUAGE_ID = "00000000-0000-0000-0000-0000000000de";

    private final JdbcTemplate jdbc;
    private final ExerciseContentCodec codec;
    private final ObjectMapper objectMapper;
    private final EvaluatorRegistry registry = EvaluatorRegistry.withDefaults();

    // A boot-időben futó Flyway az éles kódúton kezdeményez Identity/AI portokat;
    // a seed-teszthez ezeket nem hívjuk, de a kontextushoz kellenek.
    @MockitoBean
    private IdentityPointsPort identityPointsPort;

    @MockitoBean
    private AiEvaluationPort aiEvaluationPort;

    @Autowired
    SeedMigrationIT(JdbcTemplate jdbc, ExerciseContentCodec codec, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.codec = codec;
        this.objectMapper = objectMapper;
    }

    private record ExerciseRow(String languageId, String type, String difficulty, int maxPoints,
            boolean required, boolean duelEligible, String payload, String solution) {
    }

    // A seed mindkét nyelv (angol V6 + német V7) feladatait tölti.
    private List<ExerciseRow> seededExercises() {
        return jdbc.query(
                "SELECT l.language_id::text AS language_id, e.type, e.difficulty, e.max_points, "
                        + "e.required_for_completion, e.duel_eligible, "
                        + "e.payload::text AS payload, e.solution::text AS solution "
                        + "FROM exercises e JOIN lessons l ON e.lesson_id = l.id "
                        + "WHERE l.language_id IN ('" + EN_LANGUAGE_ID + "', '" + DE_LANGUAGE_ID + "')",
                (rs, n) -> new ExerciseRow(rs.getString("language_id"), rs.getString("type"),
                        rs.getString("difficulty"), rs.getInt("max_points"),
                        rs.getBoolean("required_for_completion"),
                        rs.getBoolean("duel_eligible"), rs.getString("payload"), rs.getString("solution")));
    }

    @Test
    void seedLoadsExpectedCounts() {
        // Két nyelv: angol (en) és német (de), mindkettő engedélyezve.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM languages WHERE id = '" + EN_LANGUAGE_ID + "' AND code = 'en'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM languages WHERE id = '" + DE_LANGUAGE_ID + "' AND code = 'de'",
                Integer.class)).isEqualTo(1);

        // Angol: >=8 lecke, folytonos order_index 1-től.
        List<Integer> enLessonOrders = jdbc.queryForList(
                "SELECT order_index FROM lessons "
                        + "WHERE language_id = '" + EN_LANGUAGE_ID + "' ORDER BY order_index",
                Integer.class);
        assertThat(enLessonOrders).hasSizeGreaterThanOrEqualTo(8);
        assertThat(enLessonOrders).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, enLessonOrders.size()).boxed().toList());

        // Német: pontosan 3 lecke, order_index 1..3.
        List<Integer> deLessonOrders = jdbc.queryForList(
                "SELECT order_index FROM lessons "
                        + "WHERE language_id = '" + DE_LANGUAGE_ID + "' ORDER BY order_index",
                Integer.class);
        assertThat(deLessonOrders).containsExactly(1, 2, 3);

        List<ExerciseRow> all = seededExercises();

        // Minden lecke (angol ÉS német) auto >=5 és AI >=2.
        assertThat(autoCountByLesson(EN_LANGUAGE_ID).values())
                .allSatisfy(c -> assertThat(c).isGreaterThanOrEqualTo(5));
        assertThat(aiCountByLesson(EN_LANGUAGE_ID).values())
                .allSatisfy(c -> assertThat(c).isGreaterThanOrEqualTo(2));
        assertThat(autoCountByLesson(DE_LANGUAGE_ID).values())
                .allSatisfy(c -> assertThat(c).isGreaterThanOrEqualTo(5));
        assertThat(aiCountByLesson(DE_LANGUAGE_ID).values())
                .allSatisfy(c -> assertThat(c).isGreaterThanOrEqualTo(2));
        assertThat(autoCountByLesson(EN_LANGUAGE_ID)).hasSize(enLessonOrders.size());
        assertThat(autoCountByLesson(DE_LANGUAGE_ID)).hasSize(3);

        // Nyelvenkénti duel_eligible auto: angol >=12, német >=6.
        long enDuelAuto = all.stream()
                .filter(e -> EN_LANGUAGE_ID.equals(e.languageId())
                        && AUTO_TYPES.contains(e.type()) && e.duelEligible())
                .count();
        assertThat(enDuelAuto).isGreaterThanOrEqualTo(12);
        long deDuelAuto = all.stream()
                .filter(e -> DE_LANGUAGE_ID.equals(e.languageId())
                        && AUTO_TYPES.contains(e.type()) && e.duelEligible())
                .count();
        assertThat(deDuelAuto).isGreaterThanOrEqualTo(6);

        // Minden auto típus képviselve a seedelt tartalomban, mindegyikből >=2.
        Map<String, Long> countByAutoType = all.stream()
                .filter(e -> AUTO_TYPES.contains(e.type()))
                .collect(Collectors.groupingBy(ExerciseRow::type, Collectors.counting()));
        assertThat(countByAutoType.keySet()).containsAll(AUTO_TYPES);
        assertThat(countByAutoType.values()).allSatisfy(c -> assertThat(c).isGreaterThanOrEqualTo(2));

        // AI feladatok NEM duel_eligible, és required_for_completion=false.
        assertThat(all).filteredOn(e -> AI_TYPES.contains(e.type()))
                .allSatisfy(e -> {
                    assertThat(e.duelEligible()).isFalse();
                    assertThat(e.required()).isFalse();
                });
        // Auto feladatok required_for_completion=true.
        assertThat(all).filteredOn(e -> AUTO_TYPES.contains(e.type()))
                .allSatisfy(e -> assertThat(e.required()).isTrue());
    }

    private Map<String, Long> autoCountByLesson(String languageId) {
        return countByLesson(languageId,
                "('MULTIPLE_CHOICE','TRUE_FALSE','MATCHING','ODD_ONE_OUT','ERROR_FINDING')");
    }

    private Map<String, Long> aiCountByLesson(String languageId) {
        return countByLesson(languageId, "('TRANSLATION','AI_SCENARIO')");
    }

    private Map<String, Long> countByLesson(String languageId, String typeInClause) {
        return jdbc.query(
                "SELECT l.order_index AS oi, count(*) AS c FROM exercises e "
                        + "JOIN lessons l ON e.lesson_id = l.id "
                        + "WHERE l.language_id = '" + languageId + "' "
                        + "AND e.type IN " + typeInClause + " "
                        + "GROUP BY l.order_index",
                rs -> {
                    Map<String, Long> m = new java.util.HashMap<>();
                    while (rs.next()) {
                        m.put(Integer.toString(rs.getInt("oi")), rs.getLong("c"));
                    }
                    return m;
                });
    }

    @Test
    void germanDailyChallengeCanSelectThreeDuelEligibleExercises() {
        List<UUID> deDuelEligibleAuto = jdbc.query(
                "SELECT e.id FROM exercises e JOIN lessons l ON e.lesson_id = l.id "
                        + "WHERE l.language_id = '" + DE_LANGUAGE_ID + "' AND e.duel_eligible = TRUE "
                        + "AND e.type IN ('MULTIPLE_CHOICE','TRUE_FALSE','MATCHING','ODD_ONE_OUT','ERROR_FINDING')",
                (rs, n) -> UUID.fromString(rs.getString("id")));
        assertThat(deDuelEligibleAuto).hasSizeGreaterThanOrEqualTo(6);

        List<UUID> selected = DailyChallengeSelection.select(
                deDuelEligibleAuto, LocalDate.of(2024, 1, 1), UUID.fromString(DE_LANGUAGE_ID));
        assertThat(selected).hasSize(DailyChallengeSelection.CHALLENGE_SIZE);
        assertThat(deDuelEligibleAuto).containsAll(selected);
    }

    @Test
    void everyAutoExerciseDeserializesAndCorrectAnswerEvaluatesTrue() {
        List<ExerciseRow> autos = seededExercises().stream()
                .filter(e -> AUTO_TYPES.contains(e.type())).toList();
        assertThat(autos).isNotEmpty();

        for (ExerciseRow row : autos) {
            ExerciseType type = ExerciseType.valueOf(row.type());
            JsonNode payloadNode = objectMapper.readTree(row.payload());
            JsonNode solutionNode = objectMapper.readTree(row.solution());

            // Payload deszerializálható + validál.
            ExercisePayload payload = codec.readPayload(type, payloadNode);
            assertThat(payload).isNotNull();

            // Solution deszerializálható + validál.
            ExerciseSolution solution = codec.readSolution(type, solutionNode).orElseThrow();

            // A solution szerinti "helyes" válasz correct=true, score_ratio=1.0.
            UserAnswer correctAnswer = correctAnswerFor(solution);
            EvaluationResult result = registry.evaluate(type, solution, correctAnswer);
            assertThat(result.correct())
                    .as("auto exercise type %s correct answer must evaluate correct", type)
                    .isTrue();
            assertThat(result.scoreRatio()).isEqualByComparingTo(java.math.BigDecimal.ONE);
        }
    }

    @Test
    void maxPointsMatchesScoringFormula() {
        for (ExerciseRow row : seededExercises()) {
            if (AUTO_TYPES.contains(row.type())) {
                ExerciseType type = ExerciseType.valueOf(row.type());
                Difficulty difficulty = Difficulty.valueOf(row.difficulty());
                assertThat(row.maxPoints())
                        .as("max_points of auto %s/%s", type, difficulty)
                        .isEqualTo(ExerciseScoring.maxPoints(type, difficulty));
            } else {
                // AI-típusok rögzített maxpontja (TERV 5.1): TRANSLATION 15, AI_SCENARIO 30.
                int expected = "TRANSLATION".equals(row.type()) ? 15 : 30;
                assertThat(row.maxPoints())
                        .as("max_points of AI %s", row.type())
                        .isEqualTo(expected);
            }
        }
    }

    // A solution recordból a mindig helyes választ építi (konzisztencia bizonyíték).
    private UserAnswer correctAnswerFor(ExerciseSolution solution) {
        return switch (solution) {
            case MultipleChoiceSolution s -> new MultipleChoiceAnswer(s.correctOptionIds());
            case TrueFalseSolution s -> new TrueFalseAnswer(s.answer());
            case MatchingSolution s -> new MatchingAnswer(s.pairs().stream()
                    .map(p -> new MatchingAnswer.Pair(p.leftId(), p.rightId())).toList());
            case OddOneOutSolution s -> new OddOneOutAnswer(s.oddItemId());
            case ErrorFindingSolution s -> new ErrorFindingAnswer(s.incorrectTokenIndexes());
            default -> throw new IllegalStateException("No auto answer for solution " + solution);
        };
    }
}
