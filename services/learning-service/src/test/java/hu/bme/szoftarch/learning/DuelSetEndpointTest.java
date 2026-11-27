package hu.bme.szoftarch.learning;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.infrastructure.security.InternalApiKeyFilter;
import hu.bme.szoftarch.learning.support.ExerciseSamples;
import hu.bme.szoftarch.learning.support.TestLearningData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * MVC teszt a U7 belső párbaj-feladatkészlet végpontokra (TERV 7.5, 5.5) H2-n:
 * belső-kulcs nélkül 401, kulccsal 201 és 5 feladat; ugyanarra a {@code duelId}-re
 * ismételt POST ugyanazt a setId-t adja (idempotens); a GET megoldás nélkül, 404
 * nem létező esetre; a válasz pontozás helyes → max_points, hibás → 0, ismételt
 * azonos (set, user, position) → ugyanaz a score (idempotens).
 */
@SpringBootTest
@ActiveProfiles("test")
class DuelSetEndpointTest {

    private static final String KEY_VALUE = "test-internal-api-key-at-least-32-bytes-value";

    private final MockMvc mockMvc;
    private final TestLearningData data;
    private final ObjectMapper objectMapper;

    @Autowired
    DuelSetEndpointTest(WebApplicationContext context, FilterChainProxy springSecurityFilterChain,
            TestLearningData data, ObjectMapper objectMapper) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
        this.data = data;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void cleanUp() {
        data.deleteAll();
    }

    private UUID seedLanguageWithFiveDuelExercises() {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        // 5 duel-eligible auto feladat.
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
        // Zaj: nem-duel auto és duel-eligible AI (nem auto -> kizárva).
        data.exerciseScorable(lesson, "TRUE_FALSE", 6,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10, false);
        data.exerciseDuelEligible(lesson, "TRANSLATION", 7,
                ExerciseSamples.TRANSLATION.payloadJson(), null, 10);
        return en;
    }

    private String createBody(UUID duelId, UUID languageId) {
        return "{\"duelId\": \"" + duelId + "\", \"languageId\": \"" + languageId + "\"}";
    }

    @Test
    void createRejectedWithoutApiKey() throws Exception {
        UUID en = seedLanguageWithFiveDuelExercises();
        mockMvc.perform(post("/internal/v1/duel-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), en)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithApiKeyReturnsFiveExercisesAndIsIdempotent() throws Exception {
        UUID en = seedLanguageWithFiveDuelExercises();
        UUID duelId = UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/internal/v1/duel-sets")
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(duelId, en)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setId").exists())
                .andExpect(jsonPath("$.exercises.length()").value(5))
                .andReturn();

        String firstSetId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("setId").asString();

        // Ugyanarra a duelId-re ismételt POST: ugyanaz a setId, nem új készlet.
        MvcResult second = mockMvc.perform(post("/internal/v1/duel-sets")
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(duelId, en)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exercises.length()").value(5))
                .andReturn();

        String secondSetId = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("setId").asString();
        Assertions.assertThat(secondSetId).isEqualTo(firstSetId);
    }

    @Test
    void getReturnsSolutionFreeExercisesInSameOrderForBothReads() throws Exception {
        UUID en = seedLanguageWithFiveDuelExercises();
        UUID duelId = UUID.randomUUID();

        String createBody = mockMvc.perform(post("/internal/v1/duel-sets")
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(duelId, en)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String setId = objectMapper.readTree(createBody).get("setId").asString();

        MvcResult result = mockMvc.perform(get("/internal/v1/duel-sets/" + setId)
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises.length()").value(5))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Assertions.assertThat(body)
                .doesNotContain("solution")
                .doesNotContain("correctOptionIds")
                .doesNotContain("oddItemId")
                .doesNotContain("incorrectTokenIndexes")
                .doesNotContain("pairs");

        // Mindkét fél (két lekérés) ugyanazt az 5 feladatot kapja ugyanabban a
        // sorrendben — a mentett készlet garantálja.
        String secondBody = mockMvc.perform(get("/internal/v1/duel-sets/" + setId)
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assertions.assertThat(secondBody).isEqualTo(body);
    }

    @Test
    void getUnknownSetReturns404() throws Exception {
        mockMvc.perform(get("/internal/v1/duel-sets/" + UUID.randomUUID())
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("duel_set_not_found"));
    }

    @Test
    void answerScoresCorrectToMaxPointsWrongToZeroAndIsIdempotent() throws Exception {
        UUID en = seedLanguageWithFiveDuelExercises();
        UUID duelId = UUID.randomUUID();

        String createBody = mockMvc.perform(post("/internal/v1/duel-sets")
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(duelId, en)))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createBody);
        String setId = created.get("setId").asString();
        List<JsonNode> exercises = objectMapper.convertValue(created.get("exercises"),
                new tools.jackson.core.type.TypeReference<List<JsonNode>>() { });

        UUID userId = UUID.randomUUID();

        // Pozíció 0: helyes válasz -> max_points (10).
        JsonNode first = exercises.get(0);
        int position0 = indexOfType(exercises, first.get("type").asString());
        String correct = correctAnswerFor(first.get("type").asString());

        MvcResult correctResult = mockMvc.perform(post("/internal/v1/duel-sets/" + setId + "/answers")
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(userId, position0, correct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(position0))
                .andExpect(jsonPath("$.score").value(10))
                .andExpect(jsonPath("$.correct").value(true))
                .andReturn();

        // Idempotencia: ismételt azonos (set, user, position) eltérő válasszal sem
        // pontoz újra -> az eredeti score jön vissza.
        mockMvc.perform(post("/internal/v1/duel-sets/" + setId + "/answers")
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(userId, position0, wrongAnswerFor(first.get("type").asString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(10))
                .andExpect(jsonPath("$.correct").value(true));

        // Egy másik pozíció: hibás válasz -> 0.
        UUID otherUser = UUID.randomUUID();
        JsonNode second = exercises.get(1);
        mockMvc.perform(post("/internal/v1/duel-sets/" + setId + "/answers")
                        .header(InternalApiKeyFilter.HEADER, KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(otherUser, 1, wrongAnswerFor(second.get("type").asString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.correct").value(false));

        // A pontozás válasza nem szivárogtatja a megoldást.
        Assertions.assertThat(correctResult.getResponse().getContentAsString())
                .doesNotContain("solution")
                .doesNotContain("correctOptionIds");
    }

    @Test
    void answerWithoutApiKeyRejected() throws Exception {
        mockMvc.perform(post("/internal/v1/duel-sets/" + UUID.randomUUID() + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(UUID.randomUUID(), 0, "{\"answer\": false}")))
                .andExpect(status().isUnauthorized());
    }

    private static int indexOfType(List<JsonNode> exercises, String type) {
        for (int i = 0; i < exercises.size(); i++) {
            if (exercises.get(i).get("type").asString().equals(type)) {
                return i;
            }
        }
        throw new IllegalStateException("type not found: " + type);
    }

    private String answerBody(UUID userId, int position, String rawAnswer) {
        return "{\"userId\": \"" + userId + "\", \"position\": " + position
                + ", \"answer\": " + rawAnswer + "}";
    }

    private static String correctAnswerFor(String type) {
        return switch (type) {
            case "TRUE_FALSE" -> "{\"answer\": false}";
            case "MULTIPLE_CHOICE" -> "{\"selectedOptionIds\": [\"b\"]}";
            case "MATCHING" -> "{\"pairs\": [{\"leftId\": \"l1\", \"rightId\": \"r1\"}, "
                    + "{\"leftId\": \"l2\", \"rightId\": \"r2\"}]}";
            case "ODD_ONE_OUT" -> "{\"selectedItemId\": \"i3\"}";
            case "ERROR_FINDING" -> "{\"markedTokenIndexes\": [1]}";
            default -> throw new IllegalStateException("unexpected type " + type);
        };
    }

    private static String wrongAnswerFor(String type) {
        return switch (type) {
            case "TRUE_FALSE" -> "{\"answer\": true}";
            case "MULTIPLE_CHOICE" -> "{\"selectedOptionIds\": [\"a\"]}";
            case "MATCHING" -> "{\"pairs\": [{\"leftId\": \"l1\", \"rightId\": \"r2\"}]}";
            case "ODD_ONE_OUT" -> "{\"selectedItemId\": \"i1\"}";
            case "ERROR_FINDING" -> "{\"markedTokenIndexes\": [0]}";
            default -> throw new IllegalStateException("unexpected type " + type);
        };
    }
}
