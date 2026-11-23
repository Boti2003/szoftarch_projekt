package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.LoginRequest;
import hu.bme.szoftarch.identity.api.dto.RegisterRequest;
import hu.bme.szoftarch.identity.domain.User;
import hu.bme.szoftarch.identity.domain.UserStats;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserStatsRepository;
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
        RegisterRequest req1 = new RegisterRequest("dave2@example.com", "dave2", "Dave Two", "Password123!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

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
        RegisterRequest req1 = new RegisterRequest("dave3@example.com", "dave3", "Dave Three", "Password123!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

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
        RegisterRequest reg = new RegisterRequest("frank@example.com", "frank", "Frank F", "CorrectPass123!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

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
}
