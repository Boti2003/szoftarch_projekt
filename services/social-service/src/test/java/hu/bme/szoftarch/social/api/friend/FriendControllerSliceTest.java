package hu.bme.szoftarch.social.api.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.bme.szoftarch.social.api.error.GlobalExceptionHandler;
import hu.bme.szoftarch.social.application.common.PageQuery;
import hu.bme.szoftarch.social.application.common.PageResult;
import hu.bme.szoftarch.social.application.friend.FriendService;
import hu.bme.szoftarch.social.application.friend.FriendView;
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
 * Spring MVC slice test for {@link FriendController} (task 10.4): pagination
 * envelope (default 20 / cap 100), the missing-{@code Idempotency-Key}
 * rejection on the retryable delete, and <strong>Property 11 (Validates:
 * Requirements 4.1)</strong> — the actor handed to the service on removal is
 * the JWT {@code sub}, and the path {@code friendId} is only the target.
 *
 * <p>Slice isolation matches {@link FriendRequestControllerSliceTest}: the real
 * {@code SecurityConfig} is excluded and the filter chain disabled, while the
 * {@code jwt()} post-processor still authenticates each request so the
 * {@code sub}-derived actor id reaches the controller.
 */
@WebMvcTest(controllers = FriendController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = hu.bme.szoftarch.social.infrastructure.security.SecurityConfig.class),
        excludeAutoConfiguration = {
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration.class,
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@Import({GlobalExceptionHandler.class, hu.bme.szoftarch.social.api.SliceSecurityTestConfig.class})
class FriendControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FriendService friendService;

    private static final String KEY = "idem-key-123";

    // --- Endpoint wiring + pagination envelope -----------------------------

    @Test
    void listFriendsDefaultsSizeTo20AndSerializesEnvelope() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        FriendView view = new FriendView(friend, "Alice", 42L, 3,
                Instant.parse("2024-01-01T00:00:00Z"));
        when(friendService.listFriends(eq(actor), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(view), 0, 20, 1));

        mockMvc.perform(get("/api/v1/friends")
                        .with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value(friend.toString()))
                .andExpect(jsonPath("$.items[0].displayName").value("Alice"))
                .andExpect(jsonPath("$.items[0].points").value(42))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(friendService).listFriends(eq(actor), captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(PageQuery.DEFAULT_SIZE);
    }

    @Test
    void listFriendsCapsSizeAt100() throws Exception {
        UUID actor = UUID.randomUUID();
        when(friendService.listFriends(eq(actor), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(), 0, 100, 0));

        mockMvc.perform(get("/api/v1/friends")
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));

        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(friendService).listFriends(eq(actor), captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    // --- Idempotency on the retryable delete -------------------------------

    @Test
    void removeFriendReturns204WhenKeyPresent() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID friend = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/friends/{friendId}", friend)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isNoContent());

        verify(friendService).removeFriend(actor, friend);
    }

    @Test
    void removeFriendWithoutKeyReturns400IdempotencyKeyRequired() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID friend = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/friends/{friendId}", friend)
                        .with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(friendService, never()).removeFriend(any(), any());
    }

    // --- PROPERTY 11 (Validates: Requirements 4.1 / Property 11) -----------

    /**
     * Validates: Requirements 4.1 / Property 11 — the removal actor is the JWT
     * {@code sub}; the path {@code friendId} is only the other member being
     * removed, so a caller cannot delete a friendship on another user's behalf.
     */
    @Test
    void removeFriendDerivesActorFromJwtSubNotFromPath() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID friend = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/friends/{friendId}", friend)
                        .with(jwt().jwt(j -> j.subject(sub.toString())))
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isNoContent());

        ArgumentCaptor<UUID> actorCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(friendService).removeFriend(actorCaptor.capture(), eq(friend));
        assertThat(actorCaptor.getValue())
                .as("acting user id must be the JWT sub, never the path friendId")
                .isEqualTo(sub);
    }
}
