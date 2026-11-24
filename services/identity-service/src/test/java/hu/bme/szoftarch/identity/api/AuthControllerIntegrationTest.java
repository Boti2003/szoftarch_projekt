package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.ChangePasswordRequest;
import hu.bme.szoftarch.identity.api.dto.LoginRequest;
import hu.bme.szoftarch.identity.api.dto.RegisterRequest;
import hu.bme.szoftarch.identity.domain.RefreshToken;
import hu.bme.szoftarch.identity.domain.User;
import hu.bme.szoftarch.identity.domain.UserStats;
import hu.bme.szoftarch.identity.infrastructure.persistence.RefreshTokenRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserStatsRepository;
import hu.bme.szoftarch.identity.infrastructure.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserStatsRepository userStatsRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("Registration successfully creates user and initial stats")
    void testRegisterSuccess(CapturedOutput output) throws Exception {
        RegisterRequest request = new RegisterRequest(
                "dave@example.com",
                "dave123",
                "Dave Developer",
                "Password123!"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("dave@example.com"))
                .andExpect(jsonPath("$.username").value("dave123"))
                .andExpect(jsonPath("$.displayName").value("Dave Developer"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID userId = UUID.fromString(root.get("id").asString());

        Optional<User> userOpt = userRepository.findById(userId);
        assertThat(userOpt).isPresent();

        Optional<UserStats> statsOpt = userStatsRepository.findById(userId);
        assertThat(statsOpt).isPresent();
        assertThat(statsOpt.get().getLevel()).isEqualTo(1);
        assertThat(statsOpt.get().getTotalPoints()).isEqualTo(0);

        assertThat(output.getOut()).contains("User registered successfully");
    }

    @Test
    @DisplayName("Duplicate email registration returns 409 Conflict ProblemDetails")
    void testRegisterDuplicateEmail(CapturedOutput output) throws Exception {
        registerUser("dave2@example.com", "dave2", "Dave Two", "Password123!");

        RegisterRequest reqDuplicateEmail = new RegisterRequest("dave2@example.com", "otheruser", "Other User", "Password123!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDuplicateEmail)))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("dave2@example.com")));

        assertThat(output.getOut()).contains("[AUTH_FAILURE: CONFLICT]");
    }

    @Test
    @DisplayName("Duplicate username registration returns 409 Conflict ProblemDetails")
    void testRegisterDuplicateUsername() throws Exception {
        registerUser("dave3@example.com", "dave3", "Dave Three", "Password123!");

        RegisterRequest reqDuplicateUsername = new RegisterRequest("other3@example.com", "dave3", "Other Three", "Password123!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDuplicateUsername)))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("dave3")));
    }

    @Test
    @DisplayName("Invalid registration payload returns 400 Bad Request with field errors")
    void testRegisterValidationFailure(CapturedOutput output) throws Exception {
        RegisterRequest invalid = new RegisterRequest(
                "invalid-email-format",
                "ab", // Too short (min 3)
                "",   // Blank
                "short" // Too short (min 8)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        assertThat(output.getOut()).contains("[REQUEST_VALIDATION_FAILED]");
    }

    @Test
    @DisplayName("Password without number or without special character fails validation")
    void testRegisterPasswordComplexityFailure() throws Exception {
        // Missing number
        RegisterRequest noDigit = new RegisterRequest(
                "nodigit@example.com", "nodigit", "No Digit", "SpecialOnlyPass!"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noDigit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].message")
                        .value(org.hamcrest.Matchers.hasItem("Password must contain at least one digit and one special character")));

        // Missing special character
        RegisterRequest noSpecial = new RegisterRequest(
                "nospecial@example.com", "nospecial", "No Special", "DigitOnlyPass123"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noSpecial)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].message")
                        .value(org.hamcrest.Matchers.hasItem("Password must contain at least one digit and one special character")));
    }

    @Test
    @DisplayName("Login with valid email issues JWT access token and HttpOnly refresh cookie")
    void testLoginWithEmailSuccess(CapturedOutput output) throws Exception {
        RegisterRequest reg = new RegisterRequest("eva@example.com", "eva_tester", "Eva Tester", "SecretPass123!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest loginByEmail = new LoginRequest("eva@example.com", "SecretPass123!");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "TestAgent/1.0")
                        .content(objectMapper.writeValueAsString(loginByEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.user.username").value("eva_tester"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("refreshToken=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/api/v1/auth");

        assertThat(output.getOut()).contains("User logged in successfully");
    }

    @Test
    @DisplayName("Login with valid username issues JWT access token and HttpOnly refresh cookie")
    void testLoginWithUsernameSuccess() throws Exception {
        RegisterRequest reg = new RegisterRequest("felix@example.com", "felix_user", "Felix User", "SecretPass123!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest loginByUsername = new LoginRequest("felix_user", "SecretPass123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "TestAgent/1.0")
                        .content(objectMapper.writeValueAsString(loginByUsername)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("felix_user"));
    }

    @Test
    @DisplayName("Login with bad password returns 401 ProblemDetails and logs security alert")
    void testLoginBadPassword(CapturedOutput output) throws Exception {
        registerUser("frank@example.com", "frank", "Frank F", "CorrectPass123!");

        LoginRequest wrongPassword = new LoginRequest("frank@example.com", "WrongPassword!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(output.getOut()).contains("[AUTH_FAILURE: BAD_CREDENTIALS]");
    }

    @Test
    @DisplayName("Login with non-existent user returns 401 ProblemDetails")
    void testLoginUnknownUser(CapturedOutput output) throws Exception {
        LoginRequest unknown = new LoginRequest("non_existent_user", "SomePassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknown)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(output.getOut()).contains("[AUTH_FAILURE: BAD_CREDENTIALS]");
    }

    @Test
    @DisplayName("Token refresh successfully rotates refresh token and issues new access token")
    void testRefreshSuccess() throws Exception {
        String userAgent = "Mozilla/5.0 (X11; Linux x86_64)";
        registerUser("grace@example.com", "grace_user", "Grace Hopper", "ValidPass123!");
        String rawToken1 = loginAndGetRefreshToken("grace_user", "ValidPass123!", userAgent);
        assertThat(rawToken1).isNotNull();

        // Perform token refresh
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken1))
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("grace_user"))
                .andReturn();

        String rawToken2 = extractRefreshToken(refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(rawToken2).isNotNull();
        assertThat(rawToken2).isNotEqualTo(rawToken1);

        // Verify DB: Old token is revoked and points to new token
        String hash1 = jwtService.sha256(rawToken1);
        Optional<RefreshToken> oldTokenOpt = refreshTokenRepository.findByTokenHash(hash1);
        assertThat(oldTokenOpt).isPresent();
        assertThat(oldTokenOpt.get().isRevoked()).isTrue();
        assertThat(oldTokenOpt.get().getReplacedById()).isNotNull();

        // Verify DB: New token is active
        String hash2 = jwtService.sha256(rawToken2);
        Optional<RefreshToken> newTokenOpt = refreshTokenRepository.findByTokenHash(hash2);
        assertThat(newTokenOpt).isPresent();
        assertThat(newTokenOpt.get().isValid()).isTrue();
        assertThat(oldTokenOpt.get().getReplacedById()).isEqualTo(newTokenOpt.get().getId());
    }

    @Test
    @DisplayName("Refresh without cookie returns 401 ProblemDetails")
    void testRefreshMissingCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Missing refresh token cookie"));
    }

    @Test
    @DisplayName("Refresh with unknown token returns 401 ProblemDetails")
    void testRefreshUnknownToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "nonexistent-token-value")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid refresh token"));
    }

    @Test
    @DisplayName("Replay attack on rotated token revokes entire token family downstream")
    void testRefreshReplayAttackRevokesFamily(CapturedOutput output) throws Exception {
        String userAgent = "Mozilla/5.0 ReplayAgent";
        registerUser("replay@example.com", "replay_user", "Replay User", "ValidPass123!");
        String rawToken1 = loginAndGetRefreshToken("replay_user", "ValidPass123!", userAgent);

        // Rotate: Token1 -> Token2 -> Token3 (Token3 is active)
        String rawToken2 = refreshAndGetToken(rawToken1, userAgent);
        String rawToken3 = refreshAndGetToken(rawToken2, userAgent);

        // Token 3 should be active right now
        Optional<RefreshToken> token3Opt = refreshTokenRepository.findByTokenHash(jwtService.sha256(rawToken3));
        assertThat(token3Opt).isPresent();
        assertThat(token3Opt.get().isValid()).isTrue();

        // Attacker tries to use already-rotated Token 1!
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken1))
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));

        // Verify security alert logged
        assertThat(output.getOut()).contains("[SECURITY_ALERT: REFRESH_TOKEN_REPLAY]");

        // Verify family revocation: Token 3 must now be revoked
        Optional<RefreshToken> token3AfterAttack = refreshTokenRepository.findByTokenHash(jwtService.sha256(rawToken3));
        assertThat(token3AfterAttack).isPresent();
        assertThat(token3AfterAttack.get().isRevoked()).isTrue();

        // Legitimate user or attacker trying to use Token 3 now fails with 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken3))
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh with mismatched User-Agent is rejected with 401 and revokes token")
    void testRefreshUserAgentMismatch(CapturedOutput output) throws Exception {
        registerUser("uamismatch@example.com", "ua_user", "UA User", "ValidPass123!");
        String rawToken = loginAndGetRefreshToken("ua_user", "ValidPass123!", "Legitimate-Browser/1.0");

        // Attempt refresh with different User-Agent
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken))
                        .header(HttpHeaders.USER_AGENT, "Attacker-Bot/2.0"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.detail").value("User-Agent mismatch"));

        assertThat(output.getOut()).contains("[SECURITY_ALERT: USER_AGENT_MISMATCH]");

        // Verify token is revoked in DB
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(jwtService.sha256(rawToken));
        assertThat(tokenOpt).isPresent();
        assertThat(tokenOpt.get().isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Refresh with expired token is rejected with 401")
    void testRefreshExpiredToken() throws Exception {
        String userAgent = "Mozilla/5.0 Test";
        registerUser("expired@example.com", "expired_user", "Expired User", "ValidPass123!");
        String rawToken = loginAndGetRefreshToken("expired_user", "ValidPass123!", userAgent);
        String tokenHash = jwtService.sha256(rawToken);

        // Expire token in database
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        token.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        refreshTokenRepository.save(token);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken))
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token has expired"));
    }

    @Test
    @DisplayName("Logout revokes refresh token and clears HttpOnly cookie with Max-Age=0")
    void testLogoutSuccess() throws Exception {
        registerUser("logout@example.com", "logout_user", "Logout User", "ValidPass123!");
        String rawToken = loginAndGetRefreshToken("logout_user", "ValidPass123!", "LogoutAgent");

        // Call Logout
        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", rawToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        String logoutCookie = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(logoutCookie).isNotNull();
        assertThat(logoutCookie).contains("Max-Age=0");
        assertThat(logoutCookie).contains("refreshToken=");

        // Verify in DB that token was revoked
        String tokenHash = jwtService.sha256(rawToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        assertThat(tokenOpt).isPresent();
        assertThat(tokenOpt.get().isRevoked()).isTrue();

        // Refresh with logged-out token fails
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken))
                        .header(HttpHeaders.USER_AGENT, "LogoutAgent"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout without cookie returns 204 No Content with clearing cookie")
    void testLogoutWithoutCookie() throws Exception {
        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn();

        String logoutCookie = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(logoutCookie).isNotNull();
        assertThat(logoutCookie).contains("Max-Age=0");
    }

    @Test
    @DisplayName("Change password successfully updates password and revokes old refresh tokens")
    void testChangePasswordSuccess() throws Exception {
        registerUser("pwd@example.com", "pwduser", "Pwd User", "OldPass123!");

        // Login to get access token and refresh token
        LoginRequest login = new LoginRequest("pwduser", "OldPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asString();
        String oldRefreshToken = extractRefreshToken(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        // Change password
        ChangePasswordRequest changeReq = new ChangePasswordRequest("OldPass123!", "NewPass456!");
        MvcResult changeResult = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        // Old password no longer works
        LoginRequest oldLogin = new LoginRequest("pwduser", "OldPass123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        // New password works
        LoginRequest newLogin = new LoginRequest("pwduser", "NewPass456!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk());

        // Old refresh token was revoked by revokeAllForUser
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", oldRefreshToken)))
                .andExpect(status().isUnauthorized());

        // Verify in DB directly that password hash was updated
        User dbUser = userRepository.findByUsername("pwduser").orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456!", dbUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPass123!", dbUser.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("Change password fails with 401 when current password is wrong")
    void testChangePasswordWrongCurrent() throws Exception {
        registerUser("wrongpwd@example.com", "wrongpwd", "Wrong Pwd", "OldPass123!");

        LoginRequest login = new LoginRequest("wrongpwd", "OldPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asString();

        ChangePasswordRequest changeReq = new ChangePasswordRequest("IncorrectPass!", "NewPass456!");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Change password fails with 409 when new password is identical to old")
    void testChangePasswordIdentical() throws Exception {
        registerUser("samepwd@example.com", "samepwd", "Same Pwd", "OldPass123!");

        LoginRequest login = new LoginRequest("samepwd", "OldPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asString();

        ChangePasswordRequest changeReq = new ChangePasswordRequest("OldPass123!", "OldPass123!");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Change password unauthenticated returns 401")
    void testChangePasswordUnauthenticated() throws Exception {
        ChangePasswordRequest changeReq = new ChangePasswordRequest("OldPass123!", "NewPass456!");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isUnauthorized());
    }

    // --- Helper Functions for Test Readability ---

    private void registerUser(String email, String username, String displayName, String password) throws Exception {
        RegisterRequest reg = new RegisterRequest(email, username, displayName, password);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());
    }

    private String loginAndGetRefreshToken(String identifier, String password, String userAgent) throws Exception {
        LoginRequest login = new LoginRequest(identifier, password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, userAgent)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return extractRefreshToken(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private String refreshAndGetToken(String rawToken, String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken))
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isOk())
                .andReturn();
        return extractRefreshToken(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private String extractRefreshToken(String setCookieHeader) {
        if (setCookieHeader == null) {
            return null;
        }
        for (String part : setCookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("refreshToken=")) {
                return trimmed.substring("refreshToken=".length());
            }
        }
        return null;
    }
}
