package hu.bme.szoftarch.identity.api.dto;

import java.util.UUID;

public record PublicProfileDto(
        UUID id,
        String username,
        String displayName,
        int level,
        long totalPoints
) {
}
