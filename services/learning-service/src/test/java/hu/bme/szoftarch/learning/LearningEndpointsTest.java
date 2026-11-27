package hu.bme.szoftarch.learning;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.UserLanguageEnrollment;
import hu.bme.szoftarch.learning.domain.repository.UserLanguageEnrollmentRepository;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class LearningEndpointsTest {

    private final MockMvc mockMvc;
    private final TestLearningData data;
    private final UserLanguageEnrollmentRepository enrollmentRepository;

    @Autowired
    LearningEndpointsTest(WebApplicationContext context, FilterChainProxy springSecurityFilterChain,
            TestLearningData data, UserLanguageEnrollmentRepository enrollmentRepository) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
        this.data = data;
        this.enrollmentRepository = enrollmentRepository;
    }

    @BeforeEach
    void cleanUp() {
        data.deleteAll();
    }

    @Test
    void getLanguagesReturnsOnlyEnabled() throws Exception {
        data.language("en", "English", true);
        data.language("de", "German", false);
        String token = TestJwtFactory.validToken(UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/languages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("en"));
    }

    @Test
    void getLanguagesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/languages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEnrollmentsReturnsOnlyOwnDataAndActiveLanguage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID de = data.language("de", "German", true);

        enrollmentRepository.save(new UserLanguageEnrollment(userId, en, true, Instant.now()));
        enrollmentRepository.save(new UserLanguageEnrollment(userId, de, false, Instant.now()));
        enrollmentRepository.save(new UserLanguageEnrollment(otherUserId, de, true, Instant.now()));

        String token = TestJwtFactory.validToken(userId.toString());
        mockMvc.perform(get("/api/v1/learning/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollments.length()").value(2))
                .andExpect(jsonPath("$.activeLanguageId").value(en.toString()));
    }

    @Test
    void getEnrollmentsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/learning/enrollments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activateLanguageKeepsAtMostOneActive() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID de = data.language("de", "German", true);
        enrollmentRepository.save(new UserLanguageEnrollment(userId, en, true, Instant.now()));

        String token = TestJwtFactory.validToken(userId.toString());
        mockMvc.perform(put("/api/v1/learning/enrollments/" + de + "/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        List<UserLanguageEnrollment> enrollments = enrollmentRepository.findByIdUserId(userId);
        long active = enrollments.stream().filter(UserLanguageEnrollment::isActive).count();
        org.assertj.core.api.Assertions.assertThat(active).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(enrollments.stream()
                        .filter(UserLanguageEnrollment::isActive)
                        .findFirst().orElseThrow().getLanguageId())
                .isEqualTo(de);
    }

    @Test
    void activateUnknownLanguageReturns404() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtFactory.validToken(userId.toString());
        mockMvc.perform(put("/api/v1/learning/enrollments/" + UUID.randomUUID() + "/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("language_not_found"));
    }

    @Test
    void getLessonsMapsStatusAndUnlock() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID l1 = data.lesson(en, 1, "Lesson 1");
        UUID l2 = data.lesson(en, 2, "Lesson 2");
        UUID l3 = data.lesson(en, 3, "Lesson 3");

        // Az első lecke COMPLETED, így a második elérhető; a harmadik nem.
        data.progressCompleted(userId, l1);
        data.progressInProgress(userId, l2);

        String token = TestJwtFactory.validToken(userId.toString());
        mockMvc.perform(get("/api/v1/languages/" + en + "/lessons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(l1.toString()))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].unlocked").value(true))
                .andExpect(jsonPath("$[1].id").value(l2.toString()))
                .andExpect(jsonPath("$[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].unlocked").value(true))
                .andExpect(jsonPath("$[2].id").value(l3.toString()))
                .andExpect(jsonPath("$[2].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$[2].unlocked").value(false));
    }

    @Test
    void getLessonsFirstLessonAlwaysUnlockedWithoutProgress() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID en = data.language("en", "English", true);
        UUID l1 = data.lesson(en, 1, "Lesson 1");
        data.lesson(en, 2, "Lesson 2");

        String token = TestJwtFactory.validToken(userId.toString());
        mockMvc.perform(get("/api/v1/languages/" + en + "/lessons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(l1.toString()))
                .andExpect(jsonPath("$[0].unlocked").value(true))
                .andExpect(jsonPath("$[1].unlocked").value(false));
    }

    @Test
    void getLessonsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/languages/" + UUID.randomUUID() + "/lessons"))
                .andExpect(status().isUnauthorized());
    }
}
