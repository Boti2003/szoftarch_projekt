package hu.bme.szoftarch.identity.api.dto;

import java.util.UUID;

public record LeaderboardEntryDto(
        long rank,
        UUID userId,
        String username,
        String displayName,
        int level,
        long totalPoints
) {
}
