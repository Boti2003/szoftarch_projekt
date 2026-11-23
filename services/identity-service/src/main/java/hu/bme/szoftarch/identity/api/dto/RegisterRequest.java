package hu.bme.szoftarch.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,30}$", message = "Username must be 3-30 characters long and contain only letters, numbers, underscores, or hyphens")
        String username,

        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name cannot exceed 100 characters")
        String displayName,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be at least 8 characters long")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[^a-zA-Z0-9\\s]).+$",
                message = "Password must contain at least one digit and one special character"
        )
        String password
) {
}

