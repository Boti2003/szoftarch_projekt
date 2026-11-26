package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.LeaderboardEntryDto;
import hu.bme.szoftarch.identity.api.dto.LeaderboardResponse;
import hu.bme.szoftarch.identity.application.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/leaderboard", "/api/v1/leaderboards"})
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * Retrieves paginated global leaderboard and the authenticated user's rank.
     */
    @GetMapping({"", "/global"})
    public ResponseEntity<LeaderboardResponse> getGlobalLeaderboard(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        UUID currentUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(leaderboardService.getGlobalLeaderboard(currentUserId, page, size));
    }

    /**
     * Retrieves the current user's individual rank and standing.
     */
    @GetMapping("/me")
    public ResponseEntity<LeaderboardEntryDto> getMyRank(@AuthenticationPrincipal Jwt jwt) {
        UUID currentUserId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(leaderboardService.getMyRanking(currentUserId));
    }
}
