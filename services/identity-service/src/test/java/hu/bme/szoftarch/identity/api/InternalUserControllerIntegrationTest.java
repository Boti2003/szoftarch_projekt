package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.AddPointsRequest;
import hu.bme.szoftarch.identity.api.dto.BatchStatsRequest;
import hu.bme.szoftarch.identity.api.dto.RegisterRequest;
import hu.bme.szoftarch.identity.application.AuthService;
import hu.bme.szoftarch.identity.domain.UserStats;
import hu.bme.szoftarch.identity.infrastructure.persistence.PointTransactionRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InternalUserControllerIntegrationTest {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AuthService authService;

    @Autowired
    private UserStatsRepository userStatsRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Value("${internal.api-key}")
    private String configuredApiKey;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private UUID createTestUser(String username, String email) {
        return authService.register(new RegisterRequest(email, username, "Test User", "Password123!")).id();
    }

    @Test
    @DisplayName("Adding points credits user, recalculates square-root level and returns response")
    void addPoints_successfulCrediting_updatesPointsAndRecalculatesLevel() throws Exception {
        UUID userId = createTestUser("gamer1", "gamer1@example.com");

        // 50 points should transition user from Level 1 to Level 2 (floor(sqrt(50/50)) + 1 = 2)
        AddPointsRequest request = new AddPointsRequest(
                50,
                "LESSON_COMPLETED",
                "lesson-uuid-1",
                "idem-key-1"
        );

        mockMvc.perform(post("/internal/v1/users/{userId}/points", userId)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.pointsAdded").value(50))
                .andExpect(jsonPath("$.totalPoints").value(50))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.nextLevelPoints").value(200)) // 2^2 * 50 = 200
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.occurredAt").isNotEmpty());

        UserStats stats = userStatsRepository.findById(userId).orElseThrow();
        assertThat(stats.getTotalPoints()).isEqualTo(50L);
        assertThat(stats.getLevel()).isEqualTo(2);
        assertThat(pointTransactionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Adding points progresses to higher level according to formula")
    void addPoints_progressesToLevel3() throws Exception {
        UUID userId = createTestUser("gamer2", "gamer2@example.com");

        // Add 50 points -> Level 2
        mockMvc.perform(post("/internal/v1/users/{userId}/points", userId)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddPointsRequest(50, "QUIZ", "q1", "key-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(2));

        // Add 150 points -> total 200 points -> floor(sqrt(200/50)) + 1 = floor(2.0) + 1 = 3
        mockMvc.perform(post("/internal/v1/users/{userId}/points", userId)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddPointsRequest(150, "QUIZ", "q2", "key-b"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(200))
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.nextLevelPoints").value(450)) // 3^2 * 50 = 450
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    @DisplayName("Submitting same idempotency key returns existing transaction without increasing points (duplicate=true)")
    void addPoints_idempotency_returnsDuplicateWithoutRecrediting() throws Exception {
        UUID userId = createTestUser("gamer3", "gamer3@example.com");

        AddPointsRequest request = new AddPointsRequest(
                75,
                "DAILY_STREAK",
                "streak-day-5",
                "idempotent-streak-75"
        );

        // First call
        mockMvc.perform(post("/internal/v1/users/{userId}/points", userId)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsAdded").value(75))
                .andExpect(jsonPath("$.totalPoints").value(75))
                .andExpect(jsonPath("$.duplicate").value(false));

        // Second call with identical idempotency key
        mockMvc.perform(post("/internal/v1/users/{userId}/points", userId)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsAdded").value(75))
                .andExpect(jsonPath("$.totalPoints").value(75)) // Not 150!
                .andExpect(jsonPath("$.duplicate").value(true));

        UserStats stats = userStatsRepository.findById(userId).orElseThrow();
        assertThat(stats.getTotalPoints()).isEqualTo(75L);
        assertThat(pointTransactionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Reusing an idempotency key for another user returns 409 Conflict ProblemDetails")
    void addPoints_idempotencyKeyReusedForDifferentUser_returns409() throws Exception {
        UUID user1 = createTestUser("user1", "user1@example.com");
        UUID user2 = createTestUser("user2", "user2@example.com");

        AddPointsRequest request = new AddPointsRequest(50, "PRACTICE", "p1", "shared-idempotency-key");

        // First user succeeds
        mockMvc.perform(post("/internal/v1/users/{userId}/points", user1)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Second user with same key fails with 409
        mockMvc.perform(post("/internal/v1/users/{userId}/points", user2)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Missing X-Internal-Api-Key header returns 401 ProblemDetails")
    void addPoints_missingApiKey_returns401ProblemDetails() throws Exception {
        UUID userId = createTestUser("gamer4", "gamer4@example.com");
        AddPointsRequest request = new AddPointsRequest(10, "LESSON", "l1", "key-unauth");

        mockMvc.perform(post("/internal/v1/users/{userId}/points", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("INTERNAL_API_KEY_INVALID"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("Invalid X-Internal-Api-Key header returns 401 ProblemDetails")
    void addPoints_invalidApiKey_returns401ProblemDetails() throws Exception {
        UUID userId = createTestUser("gamer5", "gamer5@example.com");
        AddPointsRequest request = new AddPointsRequest(10, "LESSON", "l1", "key-wrong-auth");

        mockMvc.perform(post("/internal/v1/users/{userId}/points", userId)
                        .header(API_KEY_HEADER, "definitely-wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("INTERNAL_API_KEY_INVALID"));
    }

    @Test
    @DisplayName("Adding points to non-existent user returns 404 ProblemDetails")
    void addPoints_nonExistentUser_returns404ProblemDetails() throws Exception {
        UUID nonExistent = UUID.randomUUID();
        AddPointsRequest request = new AddPointsRequest(20, "LESSON", "l1", "key-not-found");

        mockMvc.perform(post("/internal/v1/users/{userId}/points", nonExistent)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Invalid payload (points < 1, missing idempotency key) returns 400 ProblemDetails")
    void addPoints_invalidPayload_returns400ProblemDetails() throws Exception {
        UUID userId = createTestUser("gamer6", "gamer6@example.com");
        AddPointsRequest request = new AddPointsRequest(0, "", "l1", "");

        mockMvc.perform(post("/internal/v1/users/{userId}/points", userId)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Batch stats retrieval returns public profile and stats for requested users")
    void getBatchStats_successfulBatchRetrieval() throws Exception {
        UUID userA = createTestUser("baty_a", "baty_a@example.com");
        UUID userB = createTestUser("baty_b", "baty_b@example.com");

        // Credit points to userA
        mockMvc.perform(post("/internal/v1/users/{userId}/points", userA)
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddPointsRequest(50, "PRACTICE", "p1", "batch-k1"))))
                .andExpect(status().isOk());

        BatchStatsRequest batchRequest = new BatchStatsRequest(List.of(userA, userB));

        mockMvc.perform(post("/internal/v1/users/public-stats:batch")
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == '" + userA + "')].totalPoints").value(50))
                .andExpect(jsonPath("$[?(@.id == '" + userA + "')].level").value(2))
                .andExpect(jsonPath("$[?(@.id == '" + userB + "')].totalPoints").value(0))
                .andExpect(jsonPath("$[?(@.id == '" + userB + "')].level").value(1));
    }

    @Test
    @DisplayName("Batch stats without X-Internal-Api-Key returns 401 ProblemDetails")
    void getBatchStats_missingApiKey_returns401ProblemDetails() throws Exception {
        BatchStatsRequest batchRequest = new BatchStatsRequest(List.of(UUID.randomUUID()));

        mockMvc.perform(post("/internal/v1/users/public-stats:batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("INTERNAL_API_KEY_INVALID"));
    }

    @Test
    @DisplayName("Batch stats with empty list returns 400 ProblemDetails")
    void getBatchStats_emptyList_returns400ProblemDetails() throws Exception {
        BatchStatsRequest batchRequest = new BatchStatsRequest(List.of());

        mockMvc.perform(post("/internal/v1/users/public-stats:batch")
                        .header(API_KEY_HEADER, configuredApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
