package hu.bme.szoftarch.social;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.szoftarch.social.api.duel.DuelController;
import hu.bme.szoftarch.social.api.friend.FriendController;
import hu.bme.szoftarch.social.api.friend.FriendRequestController;
import hu.bme.szoftarch.social.api.leaderboard.LeaderboardController;
import hu.bme.szoftarch.social.api.notification.NotificationController;
import hu.bme.szoftarch.social.application.duel.DuelAnswerOrchestrator;
import hu.bme.szoftarch.social.application.duel.DuelService;
import hu.bme.szoftarch.social.application.friend.FriendRequestService;
import hu.bme.szoftarch.social.application.friend.FriendService;
import hu.bme.szoftarch.social.application.leaderboard.FriendsLeaderboardService;
import hu.bme.szoftarch.social.application.notification.NotificationService;
import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.duel.DuelStateMachine;
import hu.bme.szoftarch.social.infrastructure.identity.IdentityStatsAdapter;
import hu.bme.szoftarch.social.infrastructure.learning.LearningDuelAdapter;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendRequestRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipRepository;
import hu.bme.szoftarch.social.infrastructure.security.AuthenticatedUser;
import hu.bme.szoftarch.social.infrastructure.security.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * End-to-end bean-graph verification for the Social Service (task 12.1).
 *
 * <p>Boots the full application context (test profile, H2 datasource, Flyway
 * disabled) and asserts that one bean from every architectural layer is present
 * and wireable: API controllers, application services, domain beans, outbound
 * adapters together with their {@link RestClient} transport config, JPA
 * repositories, and the security stack. This complements the lightweight
 * {@link SocialApplicationTests#contextLoads()} check with explicit
 * cross-layer assertions.
 *
 * <p>The {@link JwtDecoder} is built lazily (it fetches the JWKS only on the
 * first token validation), and the Identity/Learning adapters are plain beans
 * until invoked, so this test passes without a live Identity, Learning, or JWKS
 * endpoint and never requires Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
class BeanGraphWiringTest {

    // API layer
    @Autowired(required = false) FriendRequestController friendRequestController;
    @Autowired(required = false) FriendController friendController;
    @Autowired(required = false) DuelController duelController;
    @Autowired(required = false) LeaderboardController leaderboardController;
    @Autowired(required = false) NotificationController notificationController;

    // Application layer
    @Autowired(required = false) FriendRequestService friendRequestService;
    @Autowired(required = false) FriendService friendService;
    @Autowired(required = false) DuelService duelService;
    @Autowired(required = false) DuelAnswerOrchestrator duelAnswerOrchestrator;
    @Autowired(required = false) FriendsLeaderboardService friendsLeaderboardService;
    @Autowired(required = false) NotificationService notificationService;

    // Domain beans
    @Autowired(required = false) Clock clock;
    @Autowired(required = false) DuelStateMachine duelStateMachine;

    // Outbound adapters + their RestClient transport beans
    @Autowired(required = false) IdentityStatsAdapter identityStatsAdapter;
    @Autowired(required = false) LearningDuelAdapter learningDuelAdapter;
    @Autowired(required = false) @org.springframework.beans.factory.annotation.Qualifier("identityRestClient") RestClient identityRestClient;
    @Autowired(required = false) @org.springframework.beans.factory.annotation.Qualifier("learningRestClient") RestClient learningRestClient;

    // Persistence layer
    @Autowired(required = false) FriendshipRepository friendshipRepository;
    @Autowired(required = false) FriendRequestRepository friendRequestRepository;
    @Autowired(required = false) DuelRepository duelRepository;
    @Autowired(required = false) DuelParticipantRepository duelParticipantRepository;

    // Security layer
    @Autowired(required = false) SecurityFilterChain securityFilterChain;
    @Autowired(required = false) JwtDecoder jwtDecoder;
    @Autowired(required = false) AuthenticatedUser authenticatedUser;
    // JwtAuthoritiesConverter is composed into the JwtAuthenticationConverter bean
    // (not a standalone bean); verifying the converter that holds it proves it is wired.
    @Autowired(required = false) JwtAuthenticationConverter jwtAuthenticationConverter;
    @Autowired(required = false) CorrelationIdFilter correlationIdFilter;

    @Test
    void everyLayerBeanIsWired() {
        assertThat(friendRequestController).as("FriendRequestController").isNotNull();
        assertThat(friendController).as("FriendController").isNotNull();
        assertThat(duelController).as("DuelController").isNotNull();
        assertThat(leaderboardController).as("LeaderboardController").isNotNull();
        assertThat(notificationController).as("NotificationController").isNotNull();

        assertThat(friendRequestService).as("FriendRequestService").isNotNull();
        assertThat(friendService).as("FriendService").isNotNull();
        assertThat(duelService).as("DuelService").isNotNull();
        assertThat(duelAnswerOrchestrator).as("DuelAnswerOrchestrator").isNotNull();
        assertThat(friendsLeaderboardService).as("FriendsLeaderboardService").isNotNull();
        assertThat(notificationService).as("NotificationService").isNotNull();

        assertThat(clock).as("Clock").isNotNull();
        assertThat(duelStateMachine).as("DuelStateMachine").isNotNull();

        assertThat(identityStatsAdapter).as("IdentityStatsAdapter").isNotNull();
        assertThat(learningDuelAdapter).as("LearningDuelAdapter").isNotNull();
        assertThat(identityRestClient).as("identityRestClient").isNotNull();
        assertThat(learningRestClient).as("learningRestClient").isNotNull();

        assertThat(friendshipRepository).as("FriendshipRepository").isNotNull();
        assertThat(friendRequestRepository).as("FriendRequestRepository").isNotNull();
        assertThat(duelRepository).as("DuelRepository").isNotNull();
        assertThat(duelParticipantRepository).as("DuelParticipantRepository").isNotNull();

        assertThat(securityFilterChain).as("SecurityFilterChain").isNotNull();
        assertThat(jwtDecoder).as("JwtDecoder").isNotNull();
        assertThat(authenticatedUser).as("AuthenticatedUser").isNotNull();
        assertThat(jwtAuthenticationConverter)
                .as("JwtAuthenticationConverter (composes JwtAuthoritiesConverter)")
                .isNotNull();
        assertThat(correlationIdFilter).as("CorrelationIdFilter").isNotNull();
    }
}
