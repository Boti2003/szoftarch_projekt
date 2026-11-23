package hu.bme.szoftarch.identity.application;

import hu.bme.szoftarch.identity.api.dto.AuthResponse;
import hu.bme.szoftarch.identity.api.dto.LoginRequest;
import hu.bme.szoftarch.identity.api.dto.RegisterRequest;
import hu.bme.szoftarch.identity.api.dto.UserSummaryDto;
import hu.bme.szoftarch.identity.application.dto.AuthSession;
import hu.bme.szoftarch.identity.application.exception.ConflictException;
import hu.bme.szoftarch.identity.application.exception.UnauthorizedException;
import hu.bme.szoftarch.identity.domain.RefreshToken;
import hu.bme.szoftarch.identity.domain.User;
import hu.bme.szoftarch.identity.domain.UserStats;
import hu.bme.szoftarch.identity.domain.UserStatus;
import hu.bme.szoftarch.identity.infrastructure.persistence.RefreshTokenRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserRepository;
import hu.bme.szoftarch.identity.infrastructure.security.JwtProperties;
import hu.bme.szoftarch.identity.infrastructure.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Registers a new user and initializes their level 1, 0-point stats.
     */
    @Transactional
    public UserSummaryDto register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        String normalizedUsername = request.username().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered: " + normalizedEmail);
        }
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ConflictException("Username is already taken: " + normalizedUsername);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(normalizedEmail, normalizedUsername, request.displayName().trim(), passwordHash);

        UserStats stats = new UserStats(user);
        user.setStats(stats);

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: userId={}, username={}", savedUser.getId(), savedUser.getUsername());

        return toSummary(savedUser);
    }

    /**
     * Authenticates a user by email or username and issues an access token and refresh token.
     */
    @Transactional
    public AuthSession login(LoginRequest request, String userAgent, String ipAddress) {
        String identifier = request.loginIdentifier().trim().toLowerCase();

        User user = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> {
                    log.warn("[AUTH_FAILURE: BAD_CREDENTIALS] Failed login attempt for identifier: {}", identifier);
                    return new UnauthorizedException("Invalid email/username or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("[AUTH_FAILURE: BAD_CREDENTIALS] Failed login attempt for identifier: {}", identifier);
            throw new UnauthorizedException("Invalid email/username or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("[AUTH_FAILURE: INACTIVE_USER] Login attempt for inactive user: {}", identifier);
            throw new UnauthorizedException("User account is inactive or suspended");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();
        String tokenHash = jwtService.sha256(rawRefreshToken);
        String uaHash = jwtService.sha256(userAgent != null ? userAgent : "");

        Instant expiresAt = Instant.now().plus(jwtProperties.getRefreshTokenTtlSeconds(), ChronoUnit.SECONDS);
        RefreshToken refreshToken = new RefreshToken(user, tokenHash, expiresAt, uaHash);
        refreshTokenRepository.save(refreshToken);

        UserSummaryDto userSummary = toSummary(user);
        AuthResponse authResponse = new AuthResponse(accessToken, jwtProperties.getAccessTokenTtlSeconds(), userSummary);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", rawRefreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(jwtProperties.getRefreshTokenTtlSeconds())
                .build();

        log.info("User logged in successfully: userId={}, username={}", user.getId(), user.getUsername());

        return new AuthSession(authResponse, cookie);
    }

    private UserSummaryDto toSummary(User user) {
        return new UserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getCreatedAt()
        );
    }
}
