package hu.bme.szoftarch.social.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.bme.szoftarch.social.application.notification.NotificationService;
import hu.bme.szoftarch.social.application.notification.NotificationSummaryView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Security slice test (task 11.2) that exercises the <strong>real</strong>
 * production {@link SecurityConfig} filter chain end to end, asserting the
 * resource-server authorization rules (requirements 4.1, 4.2, 4.3).
 *
 * <h2>Why {@code @SpringBootTest} rather than {@code @WebMvcTest}</h2>
 * The 10.4 controller slices deliberately swap in a permissive chain
 * ({@code SliceSecurityTestConfig}) so they can test wiring/DTOs without a live
 * JWKS. This task instead needs the genuine {@code SecurityFilterChain} so the
 * 401-vs-authenticated decisions and the open-actuator rules are really tested.
 * The full context is loaded under the {@code test} profile (H2, Flyway off),
 * exactly like {@code SocialApplicationTests}, so no Docker or live upstream is
 * required.
 *
 * <h2>Supplying a JwtDecoder without a live JWKS</h2>
 * Production builds the {@link JwtDecoder} lazily from Identity's JWKS URI. Here
 * that bean is replaced with a Mockito mock via {@link MockitoBean}, so nothing
 * reaches out to a real JWKS:
 * <ul>
 *   <li>Authenticated cases use Spring Security Test's {@code jwt()} request
 *       post-processor, which installs a {@code JwtAuthenticationToken} directly
 *       into the security context and never invokes the decoder — so the real
 *       chain's {@code authenticated()} rule is satisfied with a chosen
 *       {@code sub}.</li>
 *   <li>The invalid/expired case sends a real {@code Authorization: Bearer}
 *       header and stubs the mock decoder to throw {@link BadJwtException} (what
 *       the real decoder raises for an expired token or bad signature), so the
 *       real {@code BearerTokenAuthenticationFilter} runs and rejection flows
 *       through {@link ProblemAuthenticationEntryPoint}.</li>
 * </ul>
 * The application services are mocked ({@link MockitoBean}) so no database is
 * touched when an authenticated request reaches a controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    /** Replaces the production lazy JWKS-backed decoder; no live JWKS needed. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** Mocked so an authenticated request never touches the database. */
    @MockitoBean
    private NotificationService notificationService;

    // ---------------------------------------------------------------------
    // Requirement 4.3 — missing token on a protected route -> 401 problem+json
    // ---------------------------------------------------------------------

    @Test
    void protectedRouteWithoutTokenReturns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void anotherProtectedRouteWithoutTokenReturns401Problem() throws Exception {
        // A second protected group (/api/v1/friends) to prove the rule is not
        // endpoint-specific: everything under /api/v1/** requires a token.
        mockMvc.perform(get("/api/v1/friends"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void socialHealthRouteRequiresAuthentication() throws Exception {
        // SocialHealthController lives at /api/v1/social/health and, unlike the
        // actuator probes, is NOT open: only actuator health/info are permitted.
        mockMvc.perform(get("/api/v1/social/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------------------------------------------------------------------
    // Requirement 4.2 — valid token on a protected route -> not 401
    // ---------------------------------------------------------------------

    @Test
    void protectedRouteWithValidJwtIsAuthenticated() throws Exception {
        UUID sub = UUID.randomUUID();
        when(notificationService.summary(any()))
                .thenReturn(new NotificationSummaryView(0, List.of(), 0, List.of()));

        mockMvc.perform(get("/api/v1/notifications/summary")
                        .with(jwt().jwt(j -> j.subject(sub.toString()))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Requirement 4.3 — invalid / expired token -> 401 UNAUTHENTICATED
    // ---------------------------------------------------------------------

    @Test
    void protectedRouteWithInvalidTokenReturns401() throws Exception {
        // The real bearer-token filter decodes the token; the mock decoder
        // rejects it as an expired/invalid token would be rejected in production.
        // BadJwtException is what NimbusJwtDecoder raises for an expired token or
        // a bad signature; the provider maps it to InvalidBearerTokenException ->
        // 401 via the entry point (a plain JwtException would be an internal 500).
        when(jwtDecoder.decode("bad-or-expired-token"))
                .thenThrow(new BadJwtException("expired or invalid signature"));

        mockMvc.perform(get("/api/v1/notifications/summary")
                        .header("Authorization", "Bearer bad-or-expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------------------------------------------------------------------
    // Requirement 4.2 — open actuator health / info (no token required)
    // ---------------------------------------------------------------------

    @Test
    void actuatorHealthIsOpenWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorInfoIsOpenWithoutToken() throws Exception {
        // /actuator/info may be 200 (with content) or 404 (no info contributors),
        // but it must never be 401: the security rule permits it without a token.
        int statusCode = mockMvc.perform(get("/actuator/info"))
                .andReturn().getResponse().getStatus();
        assertThat(statusCode)
                .as("/actuator/info must be reachable without authentication (never 401)")
                .isNotEqualTo(401);
    }

    // ---------------------------------------------------------------------
    // Requirement 4.1 — ownership derived from the JWT `sub`, not request input
    // ---------------------------------------------------------------------

    @Test
    void actingUserIsDerivedFromJwtSubAndChangesWithIt() throws Exception {
        when(notificationService.summary(any()))
                .thenReturn(new NotificationSummaryView(0, List.of(), 0, List.of()));

        UUID subU1 = UUID.randomUUID();
        UUID forgedUserId = UUID.randomUUID();

        // Token sub = U1, with a forged userId query param that must be ignored.
        mockMvc.perform(get("/api/v1/notifications/summary")
                        .with(jwt().jwt(j -> j.subject(subU1.toString())))
                        .param("userId", forgedUserId.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<UUID> actorCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService).summary(actorCaptor.capture());
        assertThat(actorCaptor.getValue())
                .as("acting user must come from the JWT sub, never from request input")
                .isEqualTo(subU1)
                .isNotEqualTo(forgedUserId);

        // A different sub must change the captured actor, proving the actor is
        // bound to the token identity rather than anything in the request.
        UUID subU2 = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/notifications/summary")
                        .with(jwt().jwt(j -> j.subject(subU2.toString()))))
                .andExpect(status().isOk());

        ArgumentCaptor<UUID> secondCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService, org.mockito.Mockito.times(2)).summary(secondCaptor.capture());
        assertThat(secondCaptor.getAllValues())
                .as("each request's actor is its own token sub")
                .containsExactly(subU1, subU2);
    }
}
