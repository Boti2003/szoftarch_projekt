package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.RegisterRequest;
import hu.bme.szoftarch.identity.application.AuthService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeaderboardControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

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

    private User createTestUserWithPoints(String username, String email, long points, int level) {
        var summary = authService.register(new RegisterRequest(email, username, "Display " + username, "Password123!"));
        User user = userRepository.findById(summary.id()).orElseThrow();
        UserStats stats = userStatsRepository.findById(user.getId()).orElseThrow();
        stats.setTotalPoints(points);
        stats.setLevel(level);
        userStatsRepository.save(stats);
        return user;
    }

    private String getAuthHeader(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    @Test
    @DisplayName("Leaderboard returns sorted users with ranks and myRank for authenticated user")
    void getGlobalLeaderboard_authenticated_returnsSortedEntriesAndMyRank() throws Exception {
        User alice = createTestUserWithPoints("alice", "alice@example.com", 300, 3);
        User bob = createTestUserWithPoints("bob", "bob@example.com", 500, 4);
        User charlie = createTestUserWithPoints("charlie", "charlie@example.com", 100, 2);

        mockMvc.perform(get("/api/v1/leaderboard")
                        .header(HttpHeaders.AUTHORIZATION, getAuthHeader(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(3)))
                // Rank 1: Bob (500 pts)
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].username").value("bob"))
                .andExpect(jsonPath("$.entries[0].totalPoints").value(500))
                .andExpect(jsonPath("$.entries[0].level").value(4))
                // Rank 2: Alice (300 pts)
                .andExpect(jsonPath("$.entries[1].rank").value(2))
                .andExpect(jsonPath("$.entries[1].username").value("alice"))
                .andExpect(jsonPath("$.entries[1].totalPoints").value(300))
                // Rank 3: Charlie (100 pts)
                .andExpect(jsonPath("$.entries[2].rank").value(3))
                .andExpect(jsonPath("$.entries[2].username").value("charlie"))
                .andExpect(jsonPath("$.entries[2].totalPoints").value(100))
                // myRank: Alice (rank 2)
                .andExpect(jsonPath("$.myRank.rank").value(2))
                .andExpect(jsonPath("$.myRank.userId").value(alice.getId().toString()))
                .andExpect(jsonPath("$.myRank.totalPoints").value(300))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("Leaderboard calculates myRank even when current user is not in the requested page")
    void getGlobalLeaderboard_pagination_myRankCalculatedWhenNotInCurrentPage() throws Exception {
        createTestUserWithPoints("p1", "p1@example.com", 400, 3);
        createTestUserWithPoints("p2", "p2@example.com", 300, 3);
        createTestUserWithPoints("p3", "p3@example.com", 200, 2);
        User p4 = createTestUserWithPoints("p4", "p4@example.com", 100, 2);

        // p4 (rank 4) requests page 0 with size 2 (which contains ranks 1 and 2)
        mockMvc.perform(get("/api/v1/leaderboard")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, getAuthHeader(p4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[1].rank").value(2))
                .andExpect(jsonPath("$.myRank.rank").value(4))
                .andExpect(jsonPath("$.myRank.userId").value(p4.getId().toString()))
                .andExpect(jsonPath("$.myRank.totalPoints").value(100))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Individual rank endpoint returns current user's standing")
    void getMyRank_authenticated_returnsIndividualRank() throws Exception {
        createTestUserWithPoints("champ", "champ@example.com", 900, 5);
        User second = createTestUserWithPoints("second", "second@example.com", 600, 4);

        mockMvc.perform(get("/api/v1/leaderboard/me")
                        .header(HttpHeaders.AUTHORIZATION, getAuthHeader(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(2))
                .andExpect(jsonPath("$.userId").value(second.getId().toString()))
                .andExpect(jsonPath("$.username").value("second"))
                .andExpect(jsonPath("$.totalPoints").value(600))
                .andExpect(jsonPath("$.level").value(4));
    }

    @Test
    @DisplayName("Alternative paths /api/v1/leaderboards/global and /api/v1/leaderboard/global are supported")
    void getGlobalLeaderboard_supportsAlternativePaths() throws Exception {
        User u = createTestUserWithPoints("solo", "solo@example.com", 50, 1);
        String token = getAuthHeader(u);

        mockMvc.perform(get("/api/v1/leaderboard/global").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)));

        mockMvc.perform(get("/api/v1/leaderboards/global").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)));
    }

    @Test
    @DisplayName("Leaderboard endpoints reject unauthenticated requests with 401 ProblemDetails")
    void leaderboardEndpoints_unauthenticated_return401() throws Exception {
        mockMvc.perform(get("/api/v1/leaderboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/leaderboard/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
