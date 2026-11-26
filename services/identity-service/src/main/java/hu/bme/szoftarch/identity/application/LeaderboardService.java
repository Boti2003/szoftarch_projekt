package hu.bme.szoftarch.identity.application;

import hu.bme.szoftarch.identity.api.dto.LeaderboardEntryDto;
import hu.bme.szoftarch.identity.api.dto.LeaderboardResponse;
import hu.bme.szoftarch.identity.application.exception.NotFoundException;
import hu.bme.szoftarch.identity.domain.UserStats;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserStatsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LeaderboardService {

    private final UserStatsRepository userStatsRepository;

    public LeaderboardService(UserStatsRepository userStatsRepository) {
        this.userStatsRepository = userStatsRepository;
    }

    /**
     * Retrieves paginated global leaderboard and the current user's ranking.
     */
    public LeaderboardResponse getGlobalLeaderboard(UUID currentUserId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);

        Page<UserStats> statsPage = userStatsRepository.findLeaderboard(PageRequest.of(safePage, safeSize));
        long startRank = (long) safePage * safeSize + 1;

        List<LeaderboardEntryDto> entries = new ArrayList<>();
        LeaderboardEntryDto myRank = null;

        List<UserStats> content = statsPage.getContent();
        for (int i = 0; i < content.size(); i++) {
            UserStats s = content.get(i);
            long rank = startRank + i;
            LeaderboardEntryDto entry = new LeaderboardEntryDto(
                    rank,
                    s.getUserId(),
                    s.getUser().getUsername(),
                    s.getUser().getDisplayName(),
                    s.getLevel(),
                    s.getTotalPoints()
            );
            entries.add(entry);

            if (currentUserId != null && currentUserId.equals(s.getUserId())) {
                myRank = entry;
            }
        }

        // If current user is not in this page, calculate their absolute rank
        if (myRank == null && currentUserId != null) {
            myRank = userStatsRepository.findById(currentUserId)
                    .map(stats -> new LeaderboardEntryDto(
                            userStatsRepository.calculateRank(stats.getTotalPoints()),
                            stats.getUserId(),
                            stats.getUser().getUsername(),
                            stats.getUser().getDisplayName(),
                            stats.getLevel(),
                            stats.getTotalPoints()
                    ))
                    .orElse(null);
        }

        return new LeaderboardResponse(
                entries,
                myRank,
                statsPage.getNumber(),
                statsPage.getSize(),
                statsPage.getTotalElements(),
                statsPage.getTotalPages()
        );
    }

    /**
     * Retrieves the individual ranking entry for a specific user.
     */
    public LeaderboardEntryDto getMyRanking(UUID userId) {
        UserStats stats = userStatsRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User stats not found for user: " + userId));

        long rank = userStatsRepository.calculateRank(stats.getTotalPoints());
        return new LeaderboardEntryDto(
                rank,
                stats.getUserId(),
                stats.getUser().getUsername(),
                stats.getUser().getDisplayName(),
                stats.getLevel(),
                stats.getTotalPoints()
        );
    }
}
