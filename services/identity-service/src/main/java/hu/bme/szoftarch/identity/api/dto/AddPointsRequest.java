package hu.bme.szoftarch.identity.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddPointsRequest(
        @NotNull(message = "Points value is required")
        @Min(value = 1, message = "Points must be at least 1")
        Integer points,

        @NotBlank(message = "Source type is required")
        @Size(max = 40, message = "Source type cannot exceed 40 characters")
        String sourceType,

        @Size(max = 255, message = "Source ID cannot exceed 255 characters")
        String sourceId,

        @NotBlank(message = "Idempotency key is required")
        @Size(max = 255, message = "Idempotency key cannot exceed 255 characters")
        String idempotencyKey
) {
}
