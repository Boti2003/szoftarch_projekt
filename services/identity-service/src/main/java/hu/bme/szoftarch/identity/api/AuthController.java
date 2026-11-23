package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.AuthResponse;
import hu.bme.szoftarch.identity.api.dto.LoginRequest;
import hu.bme.szoftarch.identity.api.dto.RegisterRequest;
import hu.bme.szoftarch.identity.api.dto.UserSummaryDto;
import hu.bme.szoftarch.identity.application.AuthService;
import hu.bme.szoftarch.identity.application.dto.AuthSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        String ipAddress = httpRequest.getRemoteAddr();
        AuthSession session = authService.login(request, userAgent, ipAddress);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, session.refreshCookie().toString())
                .body(session.authResponse());
    }
}
