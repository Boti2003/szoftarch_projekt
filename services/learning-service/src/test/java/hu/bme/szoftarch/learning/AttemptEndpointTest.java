package hu.bme.szoftarch.learning;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.LessonProgress;
import hu.bme.szoftarch.learning.domain.model.LessonProgressId;
import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.model.ProgressStatus;
import hu.bme.szoftarch.learning.domain.port.AiEvaluationException;
import hu.bme.szoftarch.learning.domain.port.AiEvaluationPort;
import hu.bme.szoftarch.learning.domain.port.EvaluateTranslationCommand;
import hu.bme.szoftarch.learning.domain.port.TranslationEvaluation;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
import hu.bme.szoftarch.learning.domain.repository.LessonProgressRepository;
import hu.bme.szoftarch.learning.support.ExerciseSamples;
import hu.bme.szoftarch.learning.support.TestJwtFactory;
import hu.bme.szoftarch.learning.support.TestLearningData;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * MVC teszt a U4 beküldés-végpontra (TERV 7.4, 5.3) H2-n: idempotencia, 401/400
 * esetek, AI-típus kezelt 400-ja, solution-szivárgás kizárása, haladás
 * IN_PROGRESS-re lépése és az első-helyes pontszándék determinisztikus kulcsa.
 */
@SpringBootTest
@ActiveProfiles("test")
class AttemptEndpointTest {

    private final MockMvc mockMvc;
    private final TestLearningData data;
    private final ExerciseAttemptRepository attemptRepository;
    private final LessonProgressRepository lessonProgressRepository;

    @MockitoBean
    private AiEvaluationPort aiEvaluationPort;

    @Autowired
    AttemptEndpointTest(WebApplicationContext context, FilterChainProxy springSecurityFilterChain,
            TestLearningData data, ExerciseAttemptRepository attemptRepository,
            LessonProgressRepository lessonProgressRepository) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
        this.data = data;
        this.attemptRepository = attemptRepository;
        this.lessonProgressRepository = lessonProgressRepository;
    }

    @BeforeEach
    void cleanUp() {
        data.deleteAll();
    }

    private UUID seedTrueFalse(UUID lessonId, int orderIndex, boolean required) {
        return data.exerciseScorable(lessonId, "TRUE_FALSE", orderIndex,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(),
                10, required);
    }

    // A TRUE_FALSE solution answer=false, tehát a helyes válasz: {"answer": false}.
    private static final String CORRECT_ANSWER = "{\"answer\": false}";
    private static final String WRONG_ANSWER = "{\"answer\": true}";

    @Test
    void submitRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/exercises/" + UUID.randomUUID() + "/attempts")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": {\"answer\": false}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitWithoutIdempotencyKeyReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedTrueFalse(lesson, 1, true);

        mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": " + CORRECT_ANSWER + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency_key_required"));
    }

    @Test
    void submitAiScenarioTypeReturnsHandled400NotServerError() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "AI_SCENARIO", 1,
                ExerciseSamples.AI_SCENARIO.payloadJson(), null, 10, true);

        mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": {\"text\": \"hello\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("exercise_not_auto_evaluable"));
    }

    @Test
    void submitAcceptedTranslationReturnsPendingPointIntentWithoutSolution() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "TRANSLATION", 1,
                ExerciseSamples.TRANSLATION.payloadJson(), null, 10, true);

        when(aiEvaluationPort.evaluateTranslation(any())).thenReturn(
                new TranslationEvaluation(new BigDecimal("0.85"), true, "Nice work, mind the tense."));

        MvcResult result = mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": {\"text\": \"Good morning\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.pointAwardStatus").value("PENDING"))
                .andReturn();

        // A válasz nem szivárogtatja a forrásszöveget vagy bármilyen megoldást.
        String body = result.getResponse().getContentAsString();
        Assertions.assertThat(body)
                .doesNotContain("Jo reggelt")
                .doesNotContain("sourceText")
                .doesNotContain("solution");

        ExerciseAttempt attempt = attemptRepository.findByUserIdAndExerciseId(userId, exercise).getFirst();
        Assertions.assertThat(attempt.getPointIdempotencyKey())
                .isEqualTo("exercise:" + userId + ":" + exercise + ":first-success");
    }

    @Test
    void submitRejectedTranslationReturnsNotApplicableWithFeedback() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "TRANSLATION", 1,
                ExerciseSamples.TRANSLATION.payloadJson(), null, 10, true);

        when(aiEvaluationPort.evaluateTranslation(any())).thenReturn(
                new TranslationEvaluation(new BigDecimal("0.50"), false, "The meaning drifted; try again."));

        mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": {\"text\": \"wrong\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.pointAwardStatus").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.feedback").value("The meaning drifted; try again."));
    }

    @Test
    void translationEvaluationUsesTrustedPayloadConfigNotClientInput() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "TRANSLATION", 1,
                ExerciseSamples.TRANSLATION.payloadJson(), null, 10, true);

        when(aiEvaluationPort.evaluateTranslation(any())).thenReturn(
                new TranslationEvaluation(new BigDecimal("0.85"), true, "ok"));

        mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        // A kliens hamis konfigot is küld; ennek NEM szabad az AI-hoz jutnia.
                        .content("{\"answer\": {\"text\": \"Good morning\", \"sourceText\": \"HACK\","
                                + " \"targetLanguageCode\": \"xx\", \"rubric\": [\"bogus\"]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<EvaluateTranslationCommand> captor =
                ArgumentCaptor.forClass(EvaluateTranslationCommand.class);
        org.mockito.Mockito.verify(aiEvaluationPort).evaluateTranslation(captor.capture());
        EvaluateTranslationCommand command = captor.getValue();
        Assertions.assertThat(command.sourceText()).isEqualTo("Jo reggelt");
        Assertions.assertThat(command.targetLanguageCode()).isEqualTo("en");
        Assertions.assertThat(command.rubric()).containsExactly("grammar", "word choice");
        Assertions.assertThat(command.userTranslation()).isEqualTo("Good morning");
    }

    @Test
    void submitReturnsOnlyResultAndNeverLeaksSolution() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedTrueFalse(lesson, 1, true);

        MvcResult result = mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": " + CORRECT_ANSWER + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").exists())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.pointAwardStatus").value("PENDING"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Assertions.assertThat(body)
                .doesNotContain("solution")
                .doesNotContain("correctOptionIds")
                .doesNotContain("oddItemId")
                .doesNotContain("incorrectTokenIndexes")
                .doesNotContain("pairs");
    }

    @Test
    void submitTranslationWithAiFailureReturnsHandled502NotServerError() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "TRANSLATION", 1,
                ExerciseSamples.TRANSLATION.payloadJson(), null, 10, true);

        // Az AI-függőség hibát jelez (timeout / 5xx / hibás body): kezelt 502,
        // SOHA nem 500-as stacktrace.
        when(aiEvaluationPort.evaluateTranslation(any()))
                .thenThrow(new AiEvaluationException("upstream 503"));

        MvcResult result = mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": {\"text\": \"Good morning\"}}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ai_unavailable"))
                .andReturn();

        // A válasz nem szivárogtat stacktrace-t, belső üzenetet vagy megoldást.
        String body = result.getResponse().getContentAsString();
        Assertions.assertThat(body)
                .doesNotContain("upstream 503")
                .doesNotContain("Exception")
                .doesNotContain("stackTrace");

        // AI-hiba esetén nincs sem attempt, sem pontszándék: a kliens újrapróbálhat.
        List<ExerciseAttempt> attempts = attemptRepository.findByUserIdAndExerciseId(userId, exercise);
        Assertions.assertThat(attempts).isEmpty();
    }

    @Test
    void reSubmitWithSameKeyIsIdempotent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedTrueFalse(lesson, 1, true);
        String key = UUID.randomUUID().toString();
        String token = "Bearer " + TestJwtFactory.validToken(userId.toString());

        String first = mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": " + CORRECT_ANSWER + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Újraküldés eltérő válasszal, de azonos kulccsal: a tárolt eredmény jön vissza.
        String second = mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": " + WRONG_ANSWER + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertThat(second).isEqualTo(first);

        List<ExerciseAttempt> attempts = attemptRepository.findByUserIdAndExerciseId(userId, exercise);
        Assertions.assertThat(attempts).hasSize(1);
        Assertions.assertThat(attempts.getFirst().getPointAwardStatus()).isEqualTo(PointAwardStatus.PENDING);
        Assertions.assertThat(attempts.getFirst().getPointIdempotencyKey())
                .isEqualTo("exercise:" + userId + ":" + exercise + ":first-success");
    }

    @Test
    void firstSubmissionAdvancesProgressToInProgress() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID required = seedTrueFalse(lesson, 1, true);
        // Egy második kötelező, meg nem próbált feladat: a lecke nem teljesül.
        data.exerciseScorable(lesson, "MULTIPLE_CHOICE", 2,
                ExerciseSamples.MULTIPLE_CHOICE.payloadJson(), ExerciseSamples.MULTIPLE_CHOICE.solutionJson(),
                10, true);

        mockMvc.perform(post("/api/v1/exercises/" + required + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": " + CORRECT_ANSWER + "}"))
                .andExpect(status().isOk());

        LessonProgress progress = lessonProgressRepository
                .findById(new LessonProgressId(userId, lesson)).orElseThrow();
        Assertions.assertThat(progress.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        Assertions.assertThat(progress.getCompletedAt()).isNull();
    }

    @Test
    void repeatedCorrectSubmissionDoesNotCreateSecondPointIntent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedTrueFalse(lesson, 1, true);
        String token = "Bearer " + TestJwtFactory.validToken(userId.toString());

        mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": " + CORRECT_ANSWER + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointAwardStatus").value("PENDING"));

        mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": " + CORRECT_ANSWER + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointAwardStatus").value("NOT_APPLICABLE"));

        List<ExerciseAttempt> attempts = attemptRepository.findByUserIdAndExerciseId(userId, exercise);
        long pending = attempts.stream()
                .filter(a -> a.getPointAwardStatus() == PointAwardStatus.PENDING)
                .count();
        Assertions.assertThat(attempts).hasSize(2);
        Assertions.assertThat(pending).isEqualTo(1);
    }
}
