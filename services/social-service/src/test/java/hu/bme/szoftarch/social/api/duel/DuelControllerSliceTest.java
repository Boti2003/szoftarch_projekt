package hu.bme.szoftarch.social.api.duel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import hu.bme.szoftarch.social.api.duel.dto.CreateDuelRequest;
import hu.bme.szoftarch.social.api.duel.dto.SubmitAnswerRequest;
import hu.bme.szoftarch.social.api.error.GlobalExceptionHandler;
import hu.bme.szoftarch.social.application.common.PageQuery;
import hu.bme.szoftarch.social.application.common.PageResult;
import hu.bme.szoftarch.social.application.duel.AnswerResultView;
import hu.bme.szoftarch.social.application.duel.DuelAnswerOrchestrator;
import hu.bme.szoftarch.social.application.duel.DuelService;
import hu.bme.szoftarch.social.application.duel.DuelSummaryView;
import hu.bme.szoftarch.social.application.duel.DuelView;
import hu.bme.szoftarch.social.domain.common.UpstreamUnavailableException;
import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import hu.bme.szoftarch.social.domain.port.AnswerSubmission;
import java.time.Instant;
import java.util.List;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spring MVC slice test for {@link DuelController} (task 10.4).
 *
 * <p>Covers endpoint wiring (201 create, 200 list/get/exercises, void
 * accept/reject, answer submission), DTO validation, the problem+json shape for
 * a domain exception, the pagination envelope (default 20 / cap 100), the
 * missing/malformed-{@code Idempotency-Key} rejection (including the UUID-key
 * requirement on answers), and <strong>Property 11 (Validates: Requirements
 * 4.1)</strong> — the acting user id is always the JWT {@code sub}, never the
 * body's {@code opponentId} nor a path value.
 *
 * <p>Slice isolation matches the friend slice tests: the real
 * {@code SecurityConfig} is excluded and the filter chain disabled, with the
 * {@code jwt()} post-processor authenticating each request so the actor id is
 * derived from {@code sub}.
 */
@WebMvcTest(controllers = DuelController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = hu.bme.szoftarch.social.infrastructure.security.SecurityConfig.class),
        excludeAutoConfiguration = {
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration.class,
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@Import({GlobalExceptionHandler.class, hu.bme.szoftarch.social.api.SliceSecurityTestConfig.class})
class DuelControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DuelService duelService;

    @MockitoBean
    private DuelAnswerOrchestrator duelAnswerOrchestrator;

    private static final String KEY = "idem-key-123";
    private static final String UUID_KEY = "11111111-1111-1111-1111-111111111111";

    // --- 1. Endpoint wiring ------------------------------------------------

    @Test
    void createReturns201AndInvokesServiceWithOpponentAndLanguageFromBody() throws Exception {
        UUID challenger = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        UUID languageId = UUID.randomUUID();
        UUID duelId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        when(duelService.create(challenger, opponent, languageId))
                .thenReturn(new DuelView(duelId, challenger, opponent, languageId, setId,
                        DuelStatus.PENDING, Instant.parse("2024-01-04T00:00:00Z")));

        mockMvc.perform(post("/api/v1/duels")
                        .with(jwt().jwt(j -> j.subject(challenger.toString())))
                        .header("Idempotency-Key", KEY)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateDuelRequest(opponent, languageId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(duelId.toString()))
                .andExpect(jsonPath("$.challengerId").value(challenger.toString()))
                .andExpect(jsonPath("$.opponentId").value(opponent.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(duelService).create(challenger, opponent, languageId);
    }

    @Test
    void acceptReturns200AndInvokesServiceWithPathIdAndSub() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID duelId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/duels/{id}/accept", duelId)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isOk());

        verify(duelService).accept(actor, duelId);
    }

    @Test
    void submitAnswerReturns200AndForwardsSubmissionAndUuidKey() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID duelId = UUID.randomUUID();
        when(duelAnswerOrchestrator.submitAnswer(eq(actor), eq(duelId),
                any(AnswerSubmission.class), eq(UUID.fromString(UUID_KEY))))
                .thenReturn(new AnswerResultView(duelId, true, 10, 10, 1,
                        ParticipantStatus.IN_PROGRESS, DuelStatus.IN_PROGRESS, null));

        mockMvc.perform(post("/api/v1/duels/{id}/answers", duelId)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", UUID_KEY)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SubmitAnswerRequest(0, Map.of("selectedOptionIds", List.of("a"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duelId").value(duelId.toString()))
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.awardedPoints").value(10));

        ArgumentCaptor<AnswerSubmission> submissionCaptor =
                ArgumentCaptor.forClass(AnswerSubmission.class);
        verify(duelAnswerOrchestrator).submitAnswer(eq(actor), eq(duelId),
                submissionCaptor.capture(), eq(UUID.fromString(UUID_KEY)));
        assertThat(submissionCaptor.getValue().position()).isEqualTo(0);
        assertThat(submissionCaptor.getValue().answer()).containsEntry("selectedOptionIds", List.of("a"));
    }

    // --- 2. DTO validation -> 400 VALIDATION_FAILED ------------------------

    @Test
    void createWithMissingOpponentReturns400ValidationFailed() throws Exception {
        UUID challenger = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/duels")
                        .with(jwt().jwt(j -> j.subject(challenger.toString())))
                        .header("Idempotency-Key", KEY)
                        .contentType("application/json")
                        .content("{\"languageId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.opponentId").exists());

        verify(duelService, never()).create(any(), any(), any());
    }

    @Test
    void submitAnswerWithMissingAnswerReturns400ValidationFailed() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID duelId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/duels/{id}/answers", duelId)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", UUID_KEY)
                        .contentType("application/json")
                        .content("{\"position\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.answer").exists());

        verify(duelAnswerOrchestrator, never()).submitAnswer(any(), any(), any(), any());
    }

    // --- 3. ProblemDetails shape (503 UPSTREAM_UNAVAILABLE) ----------------

    @Test
    void upstreamFailureIsRenderedAsProblemJsonWith503() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID duelId = UUID.randomUUID();
        when(duelAnswerOrchestrator.submitAnswer(any(), any(), any(), any()))
                .thenThrow(new UpstreamUnavailableException("scoring is temporarily unavailable"));

        mockMvc.perform(post("/api/v1/duels/{id}/answers", duelId)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", UUID_KEY)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SubmitAnswerRequest(0, Map.of("answer", true)))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Upstream service unavailable"))
                .andExpect(jsonPath("$.type").value("urn:social:error:UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.instance").value("/api/v1/duels/" + duelId + "/answers"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    // --- 4. Pagination envelope (default 20 / cap 100) ---------------------

    @Test
    void listDefaultsSizeTo20AndSerializesEnvelope() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID duelId = UUID.randomUUID();
        DuelSummaryView summary = new DuelSummaryView(duelId, actor, UUID.randomUUID(),
                UUID.randomUUID(), DuelStatus.PENDING, ParticipantStatus.NOT_STARTED, null,
                Instant.parse("2024-01-04T00:00:00Z"));
        when(duelService.listMyDuels(eq(actor), isNull(), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(summary), 0, 20, 1));

        mockMvc.perform(get("/api/v1/duels")
                        .with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(duelId.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(duelService).listMyDuels(eq(actor), isNull(), captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(PageQuery.DEFAULT_SIZE);
    }

    @Test
    void listCapsSizeAt100AndPassesStatusFilter() throws Exception {
        UUID actor = UUID.randomUUID();
        when(duelService.listMyDuels(eq(actor), eq(DuelStatus.PENDING), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(), 0, 100, 0));

        mockMvc.perform(get("/api/v1/duels")
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .param("status", "PENDING")
                        .param("size", "250"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));

        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(duelService).listMyDuels(eq(actor), eq(DuelStatus.PENDING), captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    // --- 5. Missing / malformed Idempotency-Key ----------------------------

    @Test
    void createWithoutIdempotencyKeyReturns400IdempotencyKeyRequired() throws Exception {
        UUID challenger = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/duels")
                        .with(jwt().jwt(j -> j.subject(challenger.toString())))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateDuelRequest(UUID.randomUUID(), UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(duelService, never()).create(any(), any(), any());
    }

    @Test
    void submitAnswerWithNonUuidKeyReturns400IdempotencyKeyRequired() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID duelId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/duels/{id}/answers", duelId)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SubmitAnswerRequest(0, Map.of("answer", true)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(duelAnswerOrchestrator, never()).submitAnswer(any(), any(), any(), any());
    }

    // --- 6. PROPERTY 11 (Validates: Requirements 4.1 / Property 11) --------

    /**
     * Validates: Requirements 4.1 / Property 11 — a forged body cannot make the
     * caller act for another user. The {@code challengerId} handed to the duel
     * service is the JWT {@code sub}; the body's {@code opponentId} is forwarded
     * only as the opponent (target), never promoted to the acting challenger.
     */
    @Test
    void createDerivesChallengerFromJwtSubNotFromBody() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        UUID languageId = UUID.randomUUID();
        when(duelService.create(any(), any(), any()))
                .thenReturn(new DuelView(UUID.randomUUID(), sub, opponent, languageId,
                        UUID.randomUUID(), DuelStatus.PENDING, Instant.parse("2024-01-04T00:00:00Z")));

        mockMvc.perform(post("/api/v1/duels")
                        .with(jwt().jwt(j -> j.subject(sub.toString())))
                        .header("Idempotency-Key", KEY)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateDuelRequest(opponent, languageId))))
                .andExpect(status().isCreated());

        ArgumentCaptor<UUID> challengerCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> opponentCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(duelService).create(challengerCaptor.capture(), opponentCaptor.capture(), eq(languageId));
        assertThat(challengerCaptor.getValue())
                .as("acting challengerId must be the JWT sub, never a body value")
                .isEqualTo(sub);
        assertThat(opponentCaptor.getValue())
                .as("opponentId is the forwarded target, not the actor")
                .isEqualTo(opponent);
    }

    /**
     * Validates: Requirements 4.1 / Property 11 — the answer orchestrator's
     * acting user id is the JWT {@code sub}; the path duel id is only the duel
     * being acted on.
     */
    @Test
    void submitAnswerDerivesActorFromJwtSubNotFromPath() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID duelId = UUID.randomUUID();
        when(duelAnswerOrchestrator.submitAnswer(any(), any(), any(), any()))
                .thenReturn(new AnswerResultView(duelId, false, 0, 0, 1,
                        ParticipantStatus.IN_PROGRESS, DuelStatus.IN_PROGRESS, null));

        mockMvc.perform(post("/api/v1/duels/{id}/answers", duelId)
                        .with(jwt().jwt(j -> j.subject(sub.toString())))
                        .header("Idempotency-Key", UUID_KEY)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SubmitAnswerRequest(0, Map.of("answer", true)))))
                .andExpect(status().isOk());

        ArgumentCaptor<UUID> actorCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(duelAnswerOrchestrator).submitAnswer(actorCaptor.capture(), eq(duelId),
                any(AnswerSubmission.class), eq(UUID.fromString(UUID_KEY)));
        assertThat(actorCaptor.getValue()).isEqualTo(sub);
    }
}
