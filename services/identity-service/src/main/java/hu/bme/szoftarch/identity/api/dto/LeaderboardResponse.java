package hu.bme.szoftarch.identity.api.dto;

import java.util.List;

public record LeaderboardResponse(
        List<LeaderboardEntryDto> entries,
        LeaderboardEntryDto myRank,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
