package hu.bme.szoftarch.social.api.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import hu.bme.szoftarch.social.api.error.GlobalExceptionHandler;
import hu.bme.szoftarch.social.application.common.PageQuery;
import hu.bme.szoftarch.social.application.common.PageResult;
import hu.bme.szoftarch.social.application.friend.Direction;
import hu.bme.szoftarch.social.application.friend.FriendRequestService;
import hu.bme.szoftarch.social.application.friend.FriendRequestView;
import hu.bme.szoftarch.social.domain.common.ForbiddenException;
import hu.bme.szoftarch.social.domain.friend.FriendRequestStatus;
import java.time.Instant;
import java.util.List;
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
 * Spring MVC slice test for {@link FriendRequestController} (task 10.4).
 *
 * <p>Covers endpoint wiring, DTO validation, the RFC 9457 problem body shape,
 * the pagination envelope (default 20 / cap 100), the missing-{@code
 * Idempotency-Key} rejection, and <strong>Property 11 (Validates: Requirements
 * 4.1)</strong>: the acting user id handed to the service is always the JWT
 * {@code sub}, never a value taken from the request body.
 *
 * <h2>Slice isolation from the security task (11.1)</h2>
 * The real {@code SecurityConfig} is excluded from the slice via an
 * {@code excludeFilters} assignable-type filter, and Boot's OAuth2
 * resource-server auto-configuration is excluded too, so no live JWKS or {@code
 * JwtDecoder} is needed to load the context and these tests stay independent of
 * whatever {@code SecurityFilterChain} task 11.1 settles on. A minimal
 * permissive chain ({@link hu.bme.szoftarch.social.api.SliceSecurityTestConfig})
 * is imported so Spring Security's web integration is active and the {@code
 * jwt()} request post-processor populates the security context; this lets
 * {@code @AuthenticationPrincipal Jwt} resolve and {@code CurrentUser.require}
 * yield the {@code sub}-derived actor id. Full 401/authenticated/open-actuator
 * behavior is covered separately by task 11.2.
 */
@WebMvcTest(controllers = FriendRequestController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = hu.bme.szoftarch.social.infrastructure.security.SecurityConfig.class),
        excludeAutoConfiguration = {
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration.class,
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@Import({GlobalExceptionHandler.class, hu.bme.szoftarch.social.api.SliceSecurityTestConfig.class})
class FriendRequestControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FriendRequestService friendRequestService;

    private static final String KEY = "idem-key-123";

    // --- 1. Endpoint wiring ------------------------------------------------

    @Test
    void sendReturns201AndInvokesServiceWithReceiverFromBody() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        when(friendRequestService.send(actor, receiver))
                .thenReturn(new FriendRequestView(requestId, actor, receiver,
                        FriendRequestStatus.PENDING, now, now));

        mockMvc.perform(post("/api/v1/friend-requests")
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", KEY)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SendFriendRequestRequest(receiver))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.senderId").value(actor.toString()))
                .andExpect(jsonPath("$.receiverId").value(receiver.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(friendRequestService).send(actor, receiver);
    }

    @Test
    void acceptReturns204AndInvokesServiceWithPathIdAndSub() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/friend-requests/{id}/accept", requestId)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isNoContent());

        verify(friendRequestService).accept(actor, requestId);
    }

    @Test
    void rejectReturns204AndInvokesServiceWithPathIdAndSub() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/friend-requests/{id}/reject", requestId)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isNoContent());

        verify(friendRequestService).reject(actor, requestId);
    }

    // --- 2. DTO validation -> 400 VALIDATION_FAILED with fieldErrors -------

    @Test
    void sendWithMissingReceiverIdReturns400ValidationFailed() throws Exception {
        UUID actor = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/friend-requests")
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", KEY)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.type").value("urn:social:error:VALIDATION_FAILED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/friend-requests"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.fieldErrors.receiverId").exists());

        verify(friendRequestService, never()).send(any(), any());
    }

    // --- 3. ProblemDetails shape from a mocked domain exception ------------

    @Test
    void domainForbiddenIsRenderedAsProblemJsonWithStableCode() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ForbiddenException("only the receiver may act on this friend request"))
                .when(friendRequestService).accept(eq(actor), eq(requestId));

        mockMvc.perform(post("/api/v1/friend-requests/{id}/accept", requestId)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.type").value("urn:social:error:FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("only the receiver may act on this friend request"))
                .andExpect(jsonPath("$.instance").value(
                        "/api/v1/friend-requests/" + requestId + "/accept"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    // --- 4. Pagination envelope (default 20 / cap 100) ---------------------

    @Test
    void listDefaultsSizeTo20WhenSizeOmittedAndSerializesEnvelope() throws Exception {
        UUID actor = UUID.randomUUID();
        when(friendRequestService.list(eq(actor), eq(Direction.INCOMING),
                eq(FriendRequestStatus.PENDING), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/friend-requests")
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .param("direction", "INCOMING")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(friendRequestService).list(eq(actor), eq(Direction.INCOMING),
                eq(FriendRequestStatus.PENDING), captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(PageQuery.DEFAULT_SIZE);
        assertThat(captor.getValue().page()).isZero();
    }

    @Test
    void listCapsSizeAt100WhenOverCapRequested() throws Exception {
        UUID actor = UUID.randomUUID();
        when(friendRequestService.list(eq(actor), eq(Direction.OUTGOING),
                eq(FriendRequestStatus.PENDING), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(), 0, 100, 0));

        mockMvc.perform(get("/api/v1/friend-requests")
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .param("direction", "OUTGOING")
                        .param("status", "PENDING")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));

        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(friendRequestService).list(eq(actor), eq(Direction.OUTGOING),
                eq(FriendRequestStatus.PENDING), captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    // --- 5. Missing Idempotency-Key ----------------------------------------

    @Test
    void sendWithoutIdempotencyKeyReturns400IdempotencyKeyRequired() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/friend-requests")
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SendFriendRequestRequest(receiver))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Idempotency-Key required"));

        verify(friendRequestService, never()).send(any(), any());
    }

    @Test
    void acceptWithoutIdempotencyKeyReturns400AndDoesNotInvokeService() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/friend-requests/{id}/accept", requestId)
                        .with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(friendRequestService, never()).accept(any(), any());
    }

    // --- 6. PROPERTY 11 (Validates: Requirements 4.1 / Property 11) --------

    /**
     * Validates: Requirements 4.1 / Property 11 — identity of actor.
     *
     * <p>A forged actor cannot act for another user: the {@code senderId} handed
     * to the service is always the authenticated JWT {@code sub}, even when the
     * request body carries a different user's id in its one id field
     * ({@code receiverId}). The {@code receiverId} is forwarded only as the
     * <em>target</em>, never promoted to the acting user.
     */
    @Test
    void sendDerivesActorFromJwtSubNotFromBody() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        when(friendRequestService.send(any(), any()))
                .thenReturn(new FriendRequestView(UUID.randomUUID(), sub, someoneElse,
                        FriendRequestStatus.PENDING, now, now));

        // The body's only id field is receiverId = someoneElse; there is no actor
        // field by design, so the only possible forgery vector is the target id.
        mockMvc.perform(post("/api/v1/friend-requests")
                        .with(jwt().jwt(j -> j.subject(sub.toString())))
                        .header("Idempotency-Key", KEY)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SendFriendRequestRequest(someoneElse))))
                .andExpect(status().isCreated());

        ArgumentCaptor<UUID> senderCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> receiverCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(friendRequestService).send(senderCaptor.capture(), receiverCaptor.capture());
        assertThat(senderCaptor.getValue())
                .as("acting senderId must be the JWT sub, never a body value")
                .isEqualTo(sub);
        assertThat(receiverCaptor.getValue())
                .as("receiverId is the forwarded target, not the actor")
                .isEqualTo(someoneElse);
    }

    /**
     * Validates: Requirements 4.1 / Property 11 — the accept/reject actor is the
     * JWT {@code sub}; the path id is only the resource being acted on, so one
     * authenticated caller cannot act as another by changing the token.
     */
    @Test
    void acceptDerivesActorFromJwtSubNotFromPath() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/friend-requests/{id}/accept", requestId)
                        .with(jwt().jwt(j -> j.subject(sub.toString())))
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isNoContent());

        ArgumentCaptor<UUID> actorCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(friendRequestService).accept(actorCaptor.capture(), eq(requestId));
        assertThat(actorCaptor.getValue()).isEqualTo(sub);
    }
}
