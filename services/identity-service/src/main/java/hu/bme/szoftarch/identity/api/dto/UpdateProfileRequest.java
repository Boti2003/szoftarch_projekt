package hu.bme.szoftarch.identity.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,30}$", message = "Username must be 3-30 characters long and contain only letters, numbers, underscores, or hyphens")
        String username,

        @Size(max = 100, message = "Display name cannot exceed 100 characters")
        String displayName
) {
}
