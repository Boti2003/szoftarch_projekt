package hu.bme.szoftarch.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String email,
        String username,
        String displayName,
        Instant createdAt
) {
}

