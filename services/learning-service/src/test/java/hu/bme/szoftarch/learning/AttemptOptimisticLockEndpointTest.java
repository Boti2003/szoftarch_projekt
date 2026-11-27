package hu.bme.szoftarch.learning;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.LessonProgress;
import hu.bme.szoftarch.learning.domain.repository.LessonProgressRepository;
import hu.bme.szoftarch.learning.support.ExerciseSamples;
import hu.bme.szoftarch.learning.support.TestJwtFactory;
import hu.bme.szoftarch.learning.support.TestLearningData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * U4 follow-up #2 (TERV B): az optimista zár → 409 leképezést HTTP-szinten fedi
 * (nem csak nyers kivétel szinten). A lecke-haladás írása
 * {@link ObjectOptimisticLockingFailureException}-t dob; a válasz
 * {@code 409 application/problem+json}, {@code code=progress_conflict}.
 */
@SpringBootTest
@ActiveProfiles("test")
class AttemptOptimisticLockEndpointTest {

    private final MockMvc mockMvc;
    private final TestLearningData data;

    @MockitoBean
    private LessonProgressRepository lessonProgressRepository;

    @Autowired
    AttemptOptimisticLockEndpointTest(WebApplicationContext context,
            FilterChainProxy springSecurityFilterChain, TestLearningData data) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
        this.data = data;
    }

    @BeforeEach
    void setUp() {
        data.deleteAll();
        Mockito.when(lessonProgressRepository.save(any(LessonProgress.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(LessonProgress.class, null));
    }

    @Test
    void concurrentProgressWriteMapsToConflictOverHttp() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(),
                10, true);

        mockMvc.perform(post("/api/v1/exercises/" + exercise + "/attempts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtFactory.validToken(userId.toString()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\": {\"answer\": false}}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("progress_conflict"));
    }
}
