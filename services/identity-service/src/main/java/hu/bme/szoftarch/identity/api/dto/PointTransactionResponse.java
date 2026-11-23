package hu.bme.szoftarch.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PointTransactionResponse(
        UUID transactionId,
        UUID userId,
        int pointsAdded,
        long totalPoints,
        int level,
        long nextLevelPoints,
        boolean duplicate,
        Instant occurredAt
) {
}
