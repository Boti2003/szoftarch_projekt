package hu.bme.szoftarch.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        long totalPoints,
        int level,
        long nextLevelPoints,
        Instant createdAt
) {
}
