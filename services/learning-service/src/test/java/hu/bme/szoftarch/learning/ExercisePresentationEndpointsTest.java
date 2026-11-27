package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import hu.bme.szoftarch.learning.support.ExerciseSamples;
import hu.bme.szoftarch.learning.support.TestJwtFactory;
import hu.bme.szoftarch.learning.support.TestLearningData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * MVC teszt a U2 prezentációs végpontokra (TERV 7.4):
 * {@code GET /api/v1/lessons/{lessonId}} és {@code GET /api/v1/exercises/{exerciseId}}.
 *
 * <p>Kiemelt bizonyíték (TERV 5.2): MINDEN feladattípusnál explicit assert, hogy
 * a válasz NEM tartalmazza a solutiont — sem a lecke-metaadatban, sem a feladat
 * teljes prezentációjában.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ExercisePresentationEndpointsTest {

    private final MockMvc mockMvc;
    private final TestLearningData data;

    @Autowired
    ExercisePresentationEndpointsTest(WebApplicationContext context,
            FilterChainProxy springSecurityFilterChain, TestLearningData data) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
        this.data = data;
    }

    @BeforeEach
    void cleanUp() {
        data.deleteAll();
    }

    private String token() {
        return TestJwtFactory.validToken(UUID.randomUUID().toString());
    }

    private UUID seedExercise(UUID lessonId, int orderIndex, ExerciseSamples.Sample sample) {
        return data.exercise(lessonId, sample.type(), "EASY", orderIndex,
                "Prompt for " + sample.type(), sample.payloadJson(), sample.solutionJson(),
                10, true, true, true);
    }

    // --- GET /api/v1/lessons/{lessonId} ---------------------------------------

    @Test
    void getLessonReturnsTheoryAndExerciseMetadataInOrder() throws Exception {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lessonWithTheory(en, 1, "Lesson 1", "# Theory\nHello");
        seedExercise(lesson, 2, ExerciseSamples.TRUE_FALSE);
        seedExercise(lesson, 1, ExerciseSamples.MULTIPLE_CHOICE);

        mockMvc.perform(get("/api/v1/lessons/" + lesson)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lesson.toString()))
                .andExpect(jsonPath("$.theoryMarkdown").value("# Theory\nHello"))
                .andExpect(jsonPath("$.exercises.length()").value(2))
                .andExpect(jsonPath("$.exercises[0].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.exercises[0].orderIndex").value(1))
                .andExpect(jsonPath("$.exercises[0].maxPoints").value(10))
                .andExpect(jsonPath("$.exercises[1].type").value("TRUE_FALSE"))
                .andExpect(jsonPath("$.exercises[1].orderIndex").value(2));
    }

    @Test
    void getLessonReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/lessons/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("lesson_not_found"));
    }

    @Test
    void getLessonRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/lessons/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getExerciseReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("exercise_not_found"));
    }

    @Test
    void getExerciseReturns404WhenDisabled() throws Exception {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lessonWithTheory(en, 1, "Lesson 1", "theory");
        UUID disabled = data.exercise(lesson, "MULTIPLE_CHOICE", "EASY", 1, "Prompt",
                ExerciseSamples.MULTIPLE_CHOICE.payloadJson(),
                ExerciseSamples.MULTIPLE_CHOICE.solutionJson(), 10, true, true, false);

        mockMvc.perform(get("/api/v1/exercises/" + disabled)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getExerciseRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getExercisePresentsPayloadContentPerType() throws Exception {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lessonWithTheory(en, 1, "Lesson 1", "theory");

        UUID mc = seedExercise(lesson, 1, ExerciseSamples.MULTIPLE_CHOICE);
        mockMvc.perform(get("/api/v1/exercises/" + mc)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.content.options.length()").value(3))
                .andExpect(jsonPath("$.content.options[0].id").value("a"))
                .andExpect(jsonPath("$.content.options[0].text").value("cat"));

        UUID scenario = seedExercise(lesson, 2, ExerciseSamples.AI_SCENARIO);
        mockMvc.perform(get("/api/v1/exercises/" + scenario)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("AI_SCENARIO"))
                .andExpect(jsonPath("$.content.openingMessage").value("Welcome! What would you like?"));
    }

    // --- KRITIKUS: solution szivárgás kizárása MINDEN típusra ------------------

    @Test
    void lessonMetadataNeverContainsSolutionForAnyType() throws Exception {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lessonWithTheory(en, 1, "Lesson 1", "theory");
        int order = 1;
        for (ExerciseSamples.Sample sample : ExerciseSamples.ALL) {
            seedExercise(lesson, order++, sample);
        }

        MvcResult result = mockMvc.perform(get("/api/v1/lessons/" + lesson)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises.length()").value(ExerciseSamples.ALL.length))
                .andReturn();

        assertNoSolutionLeak(result.getResponse().getContentAsString());
    }

    @Test
    void exercisePresentationNeverContainsSolutionForAnyType() throws Exception {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lessonWithTheory(en, 1, "Lesson 1", "theory");

        int order = 1;
        for (ExerciseSamples.Sample sample : ExerciseSamples.ALL) {
            UUID exerciseId = seedExercise(lesson, order++, sample);

            MvcResult result = mockMvc.perform(get("/api/v1/exercises/" + exerciseId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value(sample.type()))
                    // A válaszban sehol nincs 'solution' kulcs, egyetlen típusnál sem.
                    .andExpect(jsonPath("$.solution").doesNotExist())
                    .andExpect(jsonPath("$.content.solution").doesNotExist())
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body)
                    .as("solution kulcs nem szerepelhet a %s prezentációban", sample.type())
                    .doesNotContain("solution");
            assertSolutionValuesAbsent(sample, body);
        }
    }

    /**
     * A tárolt solution JSON-ból kinyeri azokat a jellegzetes értékeket, amelyek
     * csak a megoldásban szerepelnek, és ellenőrzi, hogy a válaszban nincsenek.
     */
    private static void assertSolutionValuesAbsent(ExerciseSamples.Sample sample, String responseBody) {
        switch (sample.type()) {
            case "MULTIPLE_CHOICE" -> assertThat(responseBody).doesNotContain("correctOptionIds");
            case "TRUE_FALSE" -> assertThat(responseBody).doesNotContain("answer");
            case "MATCHING" -> assertThat(responseBody).doesNotContain("pairs");
            case "ODD_ONE_OUT" -> assertThat(responseBody).doesNotContain("oddItemId");
            case "ERROR_FINDING" -> assertThat(responseBody).doesNotContain("incorrectTokenIndexes");
            case "TRANSLATION", "AI_SCENARIO" -> {
                // AI-típusoknak nincs solution recordjuk; csak a 'solution' kulcs tiltott.
            }
            default -> throw new IllegalStateException("Unhandled type " + sample.type());
        }
    }

    private static void assertNoSolutionLeak(String body) {
        assertThat(body)
                .doesNotContain("solution")
                .doesNotContain("correctOptionIds")
                .doesNotContain("oddItemId")
                .doesNotContain("incorrectTokenIndexes")
                .doesNotContain("pairs");
    }
}
