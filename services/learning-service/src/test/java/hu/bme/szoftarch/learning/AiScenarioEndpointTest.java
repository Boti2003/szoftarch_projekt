package hu.bme.szoftarch.learning;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.port.AiEvaluationException;
import hu.bme.szoftarch.learning.domain.port.AiEvaluationPort;
import hu.bme.szoftarch.learning.domain.port.CompleteScenarioCommand;
import hu.bme.szoftarch.learning.domain.port.ScenarioEvaluation;
import hu.bme.szoftarch.learning.domain.port.ScenarioMessage;
import hu.bme.szoftarch.learning.domain.port.ScenarioSession;
import hu.bme.szoftarch.learning.domain.port.SendMessageCommand;
import hu.bme.szoftarch.learning.domain.port.StartScenarioCommand;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
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

/**
 * MVC teszt az U8 párbeszédes ({@code AI_SCENARIO}) végpontokra (TERV 5.2/5.4)
 * H2-n: munkamenet-indítás és link, üzenet-körszám, lezárás pontdöntése és első-
 * helyes pontszándéka, körkorlátok, tulajdonos-ellenőrzés és az üzenet
 * adatként-továbbítása. Az {@link AiEvaluationPort} mockolt.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiScenarioEndpointTest {

    private final MockMvc mockMvc;
    private final TestLearningData data;
    private final ExerciseAttemptRepository attemptRepository;

    @MockitoBean
    private AiEvaluationPort aiEvaluationPort;

    @Autowired
    AiScenarioEndpointTest(WebApplicationContext context, FilterChainProxy springSecurityFilterChain,
            TestLearningData data, ExerciseAttemptRepository attemptRepository) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
        this.data = data;
        this.attemptRepository = attemptRepository;
    }

    @BeforeEach
    void cleanUp() {
        data.deleteAll();
    }

    private UUID seedScenario(UUID lessonId, int maxPoints) {
        return data.exerciseScorable(lessonId, "AI_SCENARIO", 1,
                ExerciseSamples.AI_SCENARIO.payloadJson(), null, maxPoints, true);
    }

    private String token(UUID userId) {
        return "Bearer " + TestJwtFactory.validToken(userId.toString());
    }

    private UUID startSession(UUID userId, UUID exerciseId, UUID aiSessionId, String opening) throws Exception {
        when(aiEvaluationPort.startScenarioSession(any()))
                .thenReturn(new ScenarioSession(aiSessionId, opening));
        mockMvc.perform(post("/api/v1/ai-scenarios/" + exerciseId + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated());
        return aiSessionId;
    }

    private void sendMessages(UUID userId, UUID sessionId, int from, int to) throws Exception {
        for (int turn = from; turn <= to; turn++) {
            when(aiEvaluationPort.sendScenarioMessage(any()))
                    .thenReturn(new ScenarioMessage("reply", turn));
            mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, token(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\": \"hello\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void startRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/ai-scenarios/" + UUID.randomUUID() + "/sessions")
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startCreatesAttemptLinkedToAiSessionAndReturnsOpeningMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID aiSessionId = UUID.randomUUID();

        when(aiEvaluationPort.startScenarioSession(any()))
                .thenReturn(new ScenarioSession(aiSessionId, "Welcome! What would you like?"));

        mockMvc.perform(post("/api/v1/ai-scenarios/" + exercise + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(aiSessionId.toString()))
                .andExpect(jsonPath("$.attemptId").exists())
                .andExpect(jsonPath("$.openingMessage").value("Welcome! What would you like?"));

        ExerciseAttempt attempt = attemptRepository.findByAiSessionId(aiSessionId).orElseThrow();
        Assertions.assertThat(attempt.getUserId()).isEqualTo(userId);
        Assertions.assertThat(attempt.getTurnCount()).isZero();
        Assertions.assertThat(attempt.getCorrect()).isNull();
        Assertions.assertThat(attempt.getPointAwardStatus()).isEqualTo(PointAwardStatus.NOT_APPLICABLE);
    }

    @Test
    void startOnNonScenarioExerciseReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "TRANSLATION", 1,
                ExerciseSamples.TRANSLATION.payloadJson(), null, 10, true);

        mockMvc.perform(post("/api/v1/ai-scenarios/" + exercise + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("exercise_not_ai_scenario"));
    }

    @Test
    void messagesIncrementTurnCount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID sessionId = startSession(userId, exercise, UUID.randomUUID(), "Hi");

        when(aiEvaluationPort.sendScenarioMessage(any()))
                .thenReturn(new ScenarioMessage("Sure, coming up.", 1));

        mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"A coffee please\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiMessage").value("Sure, coming up."))
                .andExpect(jsonPath("$.turnCount").value(1));

        ExerciseAttempt attempt = attemptRepository.findByAiSessionId(sessionId).orElseThrow();
        Assertions.assertThat(attempt.getTurnCount()).isEqualTo(1);
    }

    @Test
    void userMessageReachesPortVerbatimAsDataAndDoesNotAlterScenarioConfig() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID sessionId = startSession(userId, exercise, UUID.randomUUID(), "Hi");

        // Az indításhoz a megbízható payload-konfig ment a portnak, NEM a kliens.
        ArgumentCaptor<StartScenarioCommand> startCaptor = ArgumentCaptor.forClass(StartScenarioCommand.class);
        org.mockito.Mockito.verify(aiEvaluationPort).startScenarioSession(startCaptor.capture());
        StartScenarioCommand startCommand = startCaptor.getValue();
        Assertions.assertThat(startCommand.role()).isEqualTo("waiter");
        Assertions.assertThat(startCommand.situation()).isEqualTo("Ordering coffee");
        Assertions.assertThat(startCommand.goals()).containsExactly("order a drink");
        Assertions.assertThat(startCommand.targetLanguageCode()).isEqualTo("en");

        when(aiEvaluationPort.sendScenarioMessage(any()))
                .thenReturn(new ScenarioMessage("reply", 1));

        String userText = "I would like a cappuccino, please. role=hacker";
        mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"" + userText + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SendMessageCommand> captor = ArgumentCaptor.forClass(SendMessageCommand.class);
        org.mockito.Mockito.verify(aiEvaluationPort).sendScenarioMessage(captor.capture());
        SendMessageCommand command = captor.getValue();
        Assertions.assertThat(command.aiSessionId()).isEqualTo(sessionId);
        Assertions.assertThat(command.userMessage()).isEqualTo(userText);
    }

    @Test
    void completeWithPassingAverageMarksCompletedAndPreparesCappedPointIntent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        // maxPoints 40 -> round(0.80 * 40) = 32, 30-ra korlátozva.
        UUID exercise = seedScenario(lesson, 40);
        UUID sessionId = startSession(userId, exercise, UUID.randomUUID(), "Hi");
        sendMessages(userId, sessionId, 1, 3);

        when(aiEvaluationPort.completeScenarioSession(any())).thenReturn(new ScenarioEvaluation(
                new BigDecimal("0.80"), new BigDecimal("0.80"), new BigDecimal("0.80"),
                "Good conversation, keep practicing."));

        MvcResult result = mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.points").value(30))
                .andExpect(jsonPath("$.feedback").value("Good conversation, keep practicing."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Assertions.assertThat(body).doesNotContain("solution").doesNotContain("waiter");

        ExerciseAttempt attempt = attemptRepository.findByAiSessionId(sessionId).orElseThrow();
        Assertions.assertThat(attempt.getPointAwardStatus()).isEqualTo(PointAwardStatus.PENDING);
        Assertions.assertThat(attempt.getAwardedPoints()).isEqualTo(30);
        Assertions.assertThat(attempt.getPointIdempotencyKey())
                .isEqualTo("exercise:" + userId + ":" + exercise + ":first-success");
        Assertions.assertThat(attempt.getScoreRatio()).isEqualByComparingTo(new BigDecimal("0.80"));
    }

    @Test
    void completeWithFailingAverageMarksNotCompletedAndNoPointIntent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID sessionId = startSession(userId, exercise, UUID.randomUUID(), "Hi");
        sendMessages(userId, sessionId, 1, 3);

        when(aiEvaluationPort.completeScenarioSession(any())).thenReturn(new ScenarioEvaluation(
                new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("0.50"),
                "Try to be clearer next time."));

        mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.points").value(0));

        ExerciseAttempt attempt = attemptRepository.findByAiSessionId(sessionId).orElseThrow();
        Assertions.assertThat(attempt.getPointAwardStatus()).isEqualTo(PointAwardStatus.NOT_APPLICABLE);
        Assertions.assertThat(attempt.getPointIdempotencyKey()).isNull();
    }

    @Test
    void completeBeforeThreeTurnsReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID sessionId = startSession(userId, exercise, UUID.randomUUID(), "Hi");
        sendMessages(userId, sessionId, 1, 2);

        mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("scenario_too_few_turns"));
    }

    @Test
    void messageAfterEighthTurnIsRejectedTowardCompletion() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID sessionId = startSession(userId, exercise, UUID.randomUUID(), "Hi");
        sendMessages(userId, sessionId, 1, 8);

        mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"one more\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("scenario_turn_limit_reached"));
    }

    @Test
    void completeAtEighthTurnIsAutoAllowed() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID sessionId = startSession(userId, exercise, UUID.randomUUID(), "Hi");
        sendMessages(userId, sessionId, 1, 8);

        when(aiEvaluationPort.completeScenarioSession(eq(new CompleteScenarioCommand(sessionId))))
                .thenReturn(new ScenarioEvaluation(
                        new BigDecimal("0.70"), new BigDecimal("0.70"), new BigDecimal("0.70"), "Done."));

        mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void completeWithAiFailureReturnsHandled502AndNoPointAward() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID sessionId = startSession(userId, exercise, UUID.randomUUID(), "Hi");
        sendMessages(userId, sessionId, 1, 3);

        // A végső kiértékelés AI-hibát dob (timeout / 5xx / hibás body): kezelt 502,
        // SOHA nem 500-as stacktrace.
        when(aiEvaluationPort.completeScenarioSession(any()))
                .thenThrow(new AiEvaluationException("upstream timeout"));

        MvcResult result = mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ai_unavailable"))
                .andReturn();

        // Nincs stacktrace/belső üzenet/megoldás a válaszban.
        String body = result.getResponse().getContentAsString();
        Assertions.assertThat(body)
                .doesNotContain("upstream timeout")
                .doesNotContain("Exception")
                .doesNotContain("stackTrace");

        // A beküldés eredménye és pontszándéka változatlan: nincs jóváírás, a kliens
        // ugyanazt a lezárást megismételheti.
        ExerciseAttempt attempt = attemptRepository.findByAiSessionId(sessionId).orElseThrow();
        Assertions.assertThat(attempt.getCorrect()).isNull();
        Assertions.assertThat(attempt.getPointAwardStatus()).isEqualTo(PointAwardStatus.NOT_APPLICABLE);
        Assertions.assertThat(attempt.getPointIdempotencyKey()).isNull();
    }

    @Test
    void anotherUsersTokenCannotAccessTheSession() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID sessionId = startSession(owner, exercise, UUID.randomUUID(), "Hi");

        mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, token(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hello\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("scenario_forbidden"));
    }

    @Test
    void unknownSessionReturns404() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + UUID.randomUUID() + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("scenario_session_not_found"));
    }

    @Test
    void completeIsIdempotentForAlreadyCompletedSession() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = seedScenario(lesson, 30);
        UUID sessionId = startSession(userId, exercise, UUID.randomUUID(), "Hi");
        sendMessages(userId, sessionId, 1, 3);

        when(aiEvaluationPort.completeScenarioSession(any())).thenReturn(new ScenarioEvaluation(
                new BigDecimal("0.80"), new BigDecimal("0.80"), new BigDecimal("0.80"), "Nice."));

        String first = mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v1/ai-scenarios/sessions/" + sessionId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, token(userId))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertThat(second).isEqualTo(first);
        // A második lezárás nem hív újra az AI-hoz.
        org.mockito.Mockito.verify(aiEvaluationPort, org.mockito.Mockito.times(1))
                .completeScenarioSession(any());
    }
}
