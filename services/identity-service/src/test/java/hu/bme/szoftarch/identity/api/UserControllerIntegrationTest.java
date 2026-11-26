package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.RegisterRequest;
import hu.bme.szoftarch.identity.api.dto.UpdateProfileRequest;
import hu.bme.szoftarch.identity.domain.User;
import hu.bme.szoftarch.identity.domain.UserStats;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserStatsRepository;
import hu.bme.szoftarch.identity.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserStatsRepository userStatsRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("Protected endpoint without Bearer token returns 401 ProblemDetails")
    void testProtectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Protected endpoint with invalid/tampered Bearer token returns 401 ProblemDetails")
    void testProtectedEndpointWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.tampered.jwt-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns own profile, calculated level, and next-level points")
    void testGetMyProfileSuccess() throws Exception {
        User user = registerUser("alice@example.com", "alice_me", "Alice Developer", "ValidPass123!");
        UserStats stats = userStatsRepository.findById(user.getId()).orElseThrow();
        stats.setTotalPoints(120);
        stats.setLevel(2);
        userStatsRepository.save(stats);

        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.username").value("alice_me"))
                .andExpect(jsonPath("$.displayName").value("Alice Developer"))
                .andExpect(jsonPath("$.totalPoints").value(120))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.nextLevelPoints").value(200)); // 2^2 * 50 = 200
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me updates display name")
    void testPatchMyProfileDisplayName() throws Exception {
        User user = registerUser("bob@example.com", "bob_user", "Bob Original", "ValidPass123!");
        String token = jwtService.generateAccessToken(user);

        UpdateProfileRequest updateRequest = new UpdateProfileRequest(null, "Bob Updated");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Bob Updated"))
                .andExpect(jsonPath("$.username").value("bob_user"));

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getDisplayName()).isEqualTo("Bob Updated");
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me updates username successfully when unique")
    void testPatchMyProfileUsernameSuccess() throws Exception {
        User user = registerUser("carol@example.com", "carol_old", "Carol C", "ValidPass123!");
        String token = jwtService.generateAccessToken(user);

        UpdateProfileRequest updateRequest = new UpdateProfileRequest("carol_new", null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("carol_new"));

        assertThat(userRepository.findByUsername("carol_new")).isPresent();
        assertThat(userRepository.findByUsername("carol_old")).isEmpty();
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me with already taken username returns 409 Conflict")
    void testPatchMyProfileUsernameConflict() throws Exception {
        registerUser("user1@example.com", "existing_user", "User One", "ValidPass123!");
        User user2 = registerUser("user2@example.com", "second_user", "User Two", "ValidPass123!");
        String token2 = jwtService.generateAccessToken(user2);

        UpdateProfileRequest conflictRequest = new UpdateProfileRequest("existing_user", null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictRequest)))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/v1/users/search returns users matching prefix (max 20)")
    void testSearchUsersByPrefix() throws Exception {
        registerUser("prefix1@example.com", "charlie_alpha", "Charlie A", "ValidPass123!");
        registerUser("prefix2@example.com", "charlie_beta", "Charlie B", "ValidPass123!");
        User searcher = registerUser("prefix3@example.com", "david_user", "David D", "ValidPass123!");

        String token = jwtService.generateAccessToken(searcher);

        mockMvc.perform(get("/api/v1/users/search")
                        .param("username", "charlie")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("charlie_alpha"))
                .andExpect(jsonPath("$[1].username").value("charlie_beta"));
    }

    @Test
    @DisplayName("GET /api/v1/users/{id}/public returns public profile and stats for existing user")
    void testGetPublicProfileSuccess() throws Exception {
        User target = registerUser("diana@example.com", "diana_pub", "Diana Public", "ValidPass123!");
        User requester = registerUser("req@example.com", "requester", "Requester", "ValidPass123!");
        String token = jwtService.generateAccessToken(requester);

        mockMvc.perform(get("/api/v1/users/{id}/public", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId().toString()))
                .andExpect(jsonPath("$.username").value("diana_pub"))
                .andExpect(jsonPath("$.displayName").value("Diana Public"))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.totalPoints").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/users/{id}/public returns 404 ProblemDetails when user does not exist")
    void testGetPublicProfileNotFound() throws Exception {
        User requester = registerUser("req2@example.com", "requester2", "Requester Two", "ValidPass123!");
        String token = jwtService.generateAccessToken(requester);
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{id}/public", nonExistentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private User registerUser(String email, String username, String displayName, String password) throws Exception {
        RegisterRequest reg = new RegisterRequest(email, username, displayName, password);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());
        return userRepository.findByUsername(username.toLowerCase()).orElseThrow();
    }
}
