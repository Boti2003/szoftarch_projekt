package hu.bme.szoftarch.social.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.bme.szoftarch.social.api.error.GlobalExceptionHandler;
import hu.bme.szoftarch.social.application.notification.ActionableDuelView;
import hu.bme.szoftarch.social.application.notification.NotificationService;
import hu.bme.szoftarch.social.application.notification.NotificationSummaryView;
import hu.bme.szoftarch.social.application.notification.PendingRequestView;
import hu.bme.szoftarch.social.domain.duel.DuelStatus;
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
 * Spring MVC slice test for {@link NotificationController} (task 10.4): endpoint
 * wiring (200 + summary JSON) and <strong>Property 11 (Validates: Requirements
 * 4.1)</strong> — the summary subject is the JWT {@code sub}, so a caller only
 * ever sees their own pending items.
 *
 * <p>Slice isolation matches the other 10.4 slices: the real
 * {@code SecurityConfig} is excluded and the filter chain disabled, with the
 * {@code jwt()} post-processor authenticating each request.
 */
@WebMvcTest(controllers = NotificationController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = hu.bme.szoftarch.social.infrastructure.security.SecurityConfig.class),
        excludeAutoConfiguration = {
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration.class,
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@Import({GlobalExceptionHandler.class, hu.bme.szoftarch.social.api.SliceSecurityTestConfig.class})
class NotificationControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void summaryReturns200AndSerializesCountsAndLists() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID duelId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        when(notificationService.summary(actor))
                .thenReturn(new NotificationSummaryView(
                        1, List.of(new PendingRequestView(requestId, senderId, now)),
                        1, List.of(new ActionableDuelView(duelId, DuelStatus.PENDING, opponentId, now))));

        mockMvc.perform(get("/api/v1/notifications/summary")
                        .with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingFriendRequestCount").value(1))
                .andExpect(jsonPath("$.pendingFriendRequests[0].requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.actionableDuelCount").value(1))
                .andExpect(jsonPath("$.actionableDuels[0].duelId").value(duelId.toString()))
                .andExpect(jsonPath("$.actionableDuels[0].status").value("PENDING"));

        verify(notificationService).summary(actor);
    }

    /**
     * Validates: Requirements 4.1 / Property 11 — the summary subject is the JWT
     * {@code sub}; no request parameter can make a caller read another user's
     * notifications.
     */
    @Test
    void summaryDerivesSubjectFromJwtSub() throws Exception {
        UUID sub = UUID.randomUUID();
        when(notificationService.summary(any()))
                .thenReturn(new NotificationSummaryView(0, List.of(), 0, List.of()));

        mockMvc.perform(get("/api/v1/notifications/summary")
                        .with(jwt().jwt(j -> j.subject(sub.toString())))
                        // A forged query parameter must be ignored by the controller.
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<UUID> subjectCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService).summary(subjectCaptor.capture());
        assertThat(subjectCaptor.getValue())
                .as("summary subject must be the JWT sub, never a request parameter")
                .isEqualTo(sub);
    }
}
