package hu.bme.szoftarch.social.api.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.bme.szoftarch.social.api.error.GlobalExceptionHandler;
import hu.bme.szoftarch.social.application.leaderboard.FriendsLeaderboardService;
import hu.bme.szoftarch.social.application.leaderboard.LeaderboardEntryView;
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
 * Spring MVC slice test for {@link LeaderboardController} (task 10.4): endpoint
 * wiring (200 + ranked JSON) and <strong>Property 11 (Validates: Requirements
 * 4.1)</strong> — the leaderboard anchor is the JWT {@code sub}, derived from
 * the token and never from a request parameter.
 *
 * <p>Slice isolation matches the other 10.4 slices: the real
 * {@code SecurityConfig} is excluded and the filter chain disabled, with the
 * {@code jwt()} post-processor authenticating each request.
 */
@WebMvcTest(controllers = LeaderboardController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = hu.bme.szoftarch.social.infrastructure.security.SecurityConfig.class),
        excludeAutoConfiguration = {
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration.class,
                org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@Import({GlobalExceptionHandler.class, hu.bme.szoftarch.social.api.SliceSecurityTestConfig.class})
class LeaderboardControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FriendsLeaderboardService friendsLeaderboardService;

    @Test
    void friendsReturns200AndSerializesRankedEntries() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        when(friendsLeaderboardService.friendsLeaderboard(actor))
                .thenReturn(List.of(
                        new LeaderboardEntryView(actor, "Me", 100L, 5, 1),
                        new LeaderboardEntryView(friend, "Friend", 50L, 3, 2)));

        mockMvc.perform(get("/api/v1/leaderboards/friends")
                        .with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(actor.toString()))
                .andExpect(jsonPath("$[0].points").value(100))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[1].userId").value(friend.toString()))
                .andExpect(jsonPath("$[1].rank").value(2));

        verify(friendsLeaderboardService).friendsLeaderboard(actor);
    }

    /**
     * Validates: Requirements 4.1 / Property 11 — the leaderboard is anchored on
     * the JWT {@code sub}; no request parameter can make a caller view another
     * user's friend ranking.
     */
    @Test
    void friendsDerivesAnchorFromJwtSub() throws Exception {
        UUID sub = UUID.randomUUID();
        when(friendsLeaderboardService.friendsLeaderboard(any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/leaderboards/friends")
                        .with(jwt().jwt(j -> j.subject(sub.toString())))
                        // A forged query parameter must be ignored by the controller.
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<UUID> anchorCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(friendsLeaderboardService).friendsLeaderboard(anchorCaptor.capture());
        assertThat(anchorCaptor.getValue())
                .as("leaderboard anchor must be the JWT sub, never a request parameter")
                .isEqualTo(sub);
    }
}
