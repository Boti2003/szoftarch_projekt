package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.AuthResponse;
import hu.bme.szoftarch.identity.api.dto.ChangePasswordRequest;
import hu.bme.szoftarch.identity.api.dto.LoginRequest;
import hu.bme.szoftarch.identity.api.dto.RegisterRequest;
import hu.bme.szoftarch.identity.api.dto.UserSummaryDto;
import hu.bme.szoftarch.identity.application.AuthService;
import hu.bme.szoftarch.identity.application.dto.AuthSession;
import hu.bme.szoftarch.identity.application.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Public user registration endpoint.
     */
    @PostMapping("/register")
    public ResponseEntity<UserSummaryDto> register(@Valid @RequestBody RegisterRequest request) {
        UserSummaryDto createdUser = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    /**
     * Login endpoint. Returns access token in body and sets HttpOnly refresh token cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        AuthSession session = authService.login(request, userAgent);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, session.refreshCookie().toString())
                .body(session.authResponse());
    }

    /**
     * Token refresh endpoint. Rotates refresh token and issues a new access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken,
            HttpServletRequest httpRequest) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token cookie");
        }
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        AuthSession session = authService.refresh(rawRefreshToken, userAgent);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, session.refreshCookie().toString())
                .body(session.authResponse());
    }

    /**
     * Password change endpoint. Validates current password, updates to new password,
     * revokes all previous refresh tokens globally, and returns a fresh session.
     */
    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        UUID currentUserId = UUID.fromString(jwt.getSubject());
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        AuthSession session = authService.changePassword(currentUserId, request, userAgent);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, session.refreshCookie().toString())
                .body(session.authResponse());
    }

    /**
     * Logout endpoint. Revokes the refresh token and clears the HttpOnly cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken) {
        ResponseCookie cookie = authService.logout(rawRefreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
