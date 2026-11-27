package hu.bme.szoftarch.identity.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BatchStatsRequest(
        @NotEmpty(message = "User IDs list cannot be empty")
        @Size(max = 100, message = "Batch cannot exceed 100 user IDs")
        List<UUID> userIds
) {
}
