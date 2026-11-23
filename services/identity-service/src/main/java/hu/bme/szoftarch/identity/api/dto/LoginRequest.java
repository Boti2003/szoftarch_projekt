package hu.bme.szoftarch.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Login identifier (email or username) is required")
        String loginIdentifier,

        @NotBlank(message = "Password is required")
        String password
) {
}

