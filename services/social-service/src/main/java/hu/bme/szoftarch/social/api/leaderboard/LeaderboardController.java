package hu.bme.szoftarch.social.api.leaderboard;

import hu.bme.szoftarch.social.api.CurrentUser;
import hu.bme.szoftarch.social.application.leaderboard.FriendsLeaderboardService;
import hu.bme.szoftarch.social.application.leaderboard.LeaderboardEntryView;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the friends leaderboard (requirement 7.1).
 *
 * <p>The single endpoint {@code GET /api/v1/leaderboards/friends} returns the
 * authenticated user and their friends ranked by points descending (with a
 * stable tie-break on {@code userId}), as computed by
 * {@link FriendsLeaderboardService}. The path matches
 * {@code contracts/openapi/social-api.yaml} ({@code /leaderboards/friends}).
 *
 * <h2>Actor identity (requirement 4.1)</h2>
 * The user whose friend set anchors the leaderboard is derived from the JWT
 * {@code sub} via {@link CurrentUser#require(Jwt)}, never from a request
 * parameter, so a caller can only view their own friends' ranking. This is a
 * read-only endpoint, so no {@code Idempotency-Key} is required.
 *
 * <p>The ranked entries are returned as a plain list (the leaderboard is bounded
 * by the user's friend set and is not paginated), mirroring the service's
 * {@code List<LeaderboardEntryView>} contract.
 */
@RestController
public class LeaderboardController {

    private final FriendsLeaderboardService friendsLeaderboardService;

    public LeaderboardController(FriendsLeaderboardService friendsLeaderboardService) {
        this.friendsLeaderboardService = Objects.requireNonNull(friendsLeaderboardService);
    }

    /**
     * Returns the authenticated user's friends leaderboard, ranked by points.
     *
     * @param jwt the authenticated principal (leaderboard anchor = {@code sub})
     * @return the ranked leaderboard entries
     */
    @GetMapping("/api/v1/leaderboards/friends")
    public List<LeaderboardEntryView> friends(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.require(jwt);
        return friendsLeaderboardService.friendsLeaderboard(userId);
    }
}
