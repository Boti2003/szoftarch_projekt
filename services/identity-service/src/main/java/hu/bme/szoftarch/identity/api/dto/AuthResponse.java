package hu.bme.szoftarch.identity.api.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummaryDto user
) {
    public AuthResponse(String accessToken, long expiresIn, UserSummaryDto user) {
        this(accessToken, "Bearer", expiresIn, user);
    }
}

