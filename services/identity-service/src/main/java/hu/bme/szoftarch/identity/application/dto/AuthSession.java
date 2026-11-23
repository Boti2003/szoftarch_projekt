package hu.bme.szoftarch.identity.application.dto;

import hu.bme.szoftarch.identity.api.dto.AuthResponse;
import org.springframework.http.ResponseCookie;

public record AuthSession(
        AuthResponse authResponse,
        ResponseCookie refreshCookie
) {
}

