package hu.bme.szoftarch.learning;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.daily.BusinessClock;
import hu.bme.szoftarch.learning.domain.repository.DailyChallengeRepository;
import hu.bme.szoftarch.learning.support.ExerciseSamples;
import hu.bme.szoftarch.learning.support.TestJwtFactory;
import hu.bme.szoftarch.learning.support.TestLearningData;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * MVC teszt a U6 napi kihívás végpontokra (TERV 7.4, 5.4) H2-n: 401 token
 * nélkül, 400 hiányzó/érvénytelen Idempotency-Key-re, a mai feladatsor a saját
 * státusszal és solution nélkül, valamint a lazy létrehozás az első GET-re.
 */
@SpringBootTest
@ActiveProfiles("test")
class DailyChallengeEndpointTest {

    private final MockMvc mockMvc;
    private final TestLearningData data;
    private final DailyChallengeRepository challengeRepository;
    private final BusinessClock businessClock;

    @Autowired
    DailyChallengeEndpointTest(WebApplicationContext context, FilterChainProxy springSecurityFilterChain,
            TestLearningData data, DailyChallengeRepository challengeRepository,
            BusinessClock businessClock) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
        this.data = data;
        this.challengeRepository = challengeRepository;
        this.businessClock = businessClock;
    }

    @BeforeEach
    void cleanUp() {
        data.deleteAll();
    }

    @Test
    void businessClockBeanUsesBudapestZone() {
        Assertions.assertThat(businessClock.zone()).isEqualTo(ZoneId.of("Europe/Budapest"));
    }

    private UUID seedLanguageWithThreeDailyExercises() {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        data.exerciseDuelEligible(lesson, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "MULTIPLE_CHOICE", 2,
                ExerciseSamples.MULTIPLE_CHOICE.payloadJson(), ExerciseSamples.MULTIPLE_CHOICE.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "MATCHING", 3,
                ExerciseSamples.MATCHING.payloadJson(), ExerciseSamples.MATCHING.solutionJson(), 10);
        return en;
    }

    @Test
    void getTodayRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/daily-challenges/today")
                        .param("languageId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/daily-challenges/today/exercises/" + UUID.randomUUID() + "/attempts")
                        .param("languageId", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": {\"answer\": false}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitWithoutIdempotencyKeyReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = seedLanguageWithThreeDailyExercises();

        mockMvc.perform(post("/api/v1/daily-challenges/today/exercises/" + UUID.randomUUID() + "/attempts")
                        .param("languageId", en.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": {\"answer\": false}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency_key_required"));
    }

    @Test
    void submitWithInvalidIdempotencyKeyReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = seedLanguageWithThreeDailyExercises();

        mockMvc.perform(post("/api/v1/daily-challenges/today/exercises/" + UUID.randomUUID() + "/attempts")
                        .param("languageId", en.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": {\"answer\": false}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency_key_invalid"));
    }

    @Test
    void getTodayReturnsOwnStatusSolutionFreeAndLazilyCreatesChallenge() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = seedLanguageWithThreeDailyExercises();

        LocalDate today = businessClock.today();
        Assertions.assertThat(challengeRepository.findByChallengeDateAndLanguageId(today, en)).isEmpty();

        MvcResult result = mockMvc.perform(get("/api/v1/daily-challenges/today")
                        .param("languageId", en.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").exists())
                .andExpect(jsonPath("$.exercises.length()").value(3))
                .andExpect(jsonPath("$.ownStatus.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.ownStatus.correctCount").value(0))
                .andExpect(jsonPath("$.ownStatus.bonusAwarded").value(false))
                .andReturn();

        // Lazy létrehozás: az első GET létrehozta a challenge sort.
        Assertions.assertThat(challengeRepository.findByChallengeDateAndLanguageId(today, en)).isPresent();

        // A prezentáció solution-mentes.
        String body = result.getResponse().getContentAsString();
        Assertions.assertThat(body)
                .doesNotContain("solution")
                .doesNotContain("correctOptionIds")
                .doesNotContain("oddItemId")
                .doesNotContain("incorrectTokenIndexes")
                .doesNotContain("pairs")
                .doesNotContain("\"answer\":");
    }

    @Test
    void repeatedGetTodayReturnsSameChallengeId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = seedLanguageWithThreeDailyExercises();
        String token = "Bearer " + TestJwtFactory.validToken(userId.toString());

        String first = mockMvc.perform(get("/api/v1/daily-challenges/today")
                        .param("languageId", en.toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(get("/api/v1/daily-challenges/today")
                        .param("languageId", en.toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertThat(second).isEqualTo(first);
    }
}
