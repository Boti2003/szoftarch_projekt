package hu.bme.szoftarch.identity.application;

import hu.bme.szoftarch.identity.api.dto.AuthResponse;
import hu.bme.szoftarch.identity.api.dto.ChangePasswordRequest;
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
import java.util.UUID;

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
    public AuthSession login(LoginRequest request, String userAgent) {
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

        log.info("User logged in successfully: userId={}, username={}", user.getId(), user.getUsername());
        return createSession(user, userAgent);
    }

    /**
     * Changes password for an authenticated user.
     * Verifies current password, prevents reuse, updates hash, revokes all refresh tokens globally,
     * and returns a fresh session for the current client.
     */
    @Transactional
    public AuthSession changePassword(UUID userId, ChangePasswordRequest request, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("[AUTH_FAILURE: BAD_CREDENTIALS] Password change failed for userId: {}", userId);
            throw new UnauthorizedException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ConflictException("New password cannot be identical to the current password");
        }

        // saveAndFlush ensures the new password hash is persisted to the database before bulk token revocation
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        User savedUser = userRepository.saveAndFlush(user);

        // Revoke all existing refresh tokens across all families (devices)
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Password changed and all refresh tokens revoked for userId: {}", userId);

        return createSession(savedUser, userAgent);
    }
    private AuthSession createSession(User user, String userAgent) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();
        String tokenHash = jwtService.sha256(rawRefreshToken);
        String uaHash = jwtService.sha256(userAgent != null ? userAgent : "");

        Instant expiresAt = Instant.now().plus(jwtProperties.getRefreshTokenTtlSeconds(), ChronoUnit.SECONDS);
        RefreshToken refreshToken = new RefreshToken(user, tokenHash, expiresAt, uaHash);
        refreshTokenRepository.save(refreshToken);

        UserSummaryDto userSummary = toSummary(user);
        AuthResponse authResponse = new AuthResponse(accessToken, jwtProperties.getAccessTokenTtlSeconds(), userSummary);
        ResponseCookie cookie = buildRefreshCookie(rawRefreshToken, jwtProperties.getRefreshTokenTtlSeconds());

        return new AuthSession(authResponse, cookie);
    }

    /**
     * Refreshes access token and rotates refresh token (RTR).
     * Enforces replay detection (family revocation) and User-Agent fingerprint validation.
     */
    @Transactional
    public AuthSession refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token");
        }

        String tokenHash = jwtService.sha256(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("[AUTH_FAILURE: INVALID_REFRESH_TOKEN] Refresh token not found");
                    return new UnauthorizedException("Invalid refresh token");
                });

        // Replay attack detection: token was already revoked or replaced
        if (token.isRevoked() || token.isReplaced()) {
            log.warn("[SECURITY_ALERT: REFRESH_TOKEN_REPLAY] Replay attack detected for token id: {}, user: {}",
                    token.getId(), token.getUser().getId());
            revokeTokenFamily(token);
            throw new UnauthorizedException("Invalid or revoked refresh token");
        }

        // Expiration check
        if (token.isExpired()) {
            log.info("Refresh token expired for user: {}", token.getUser().getId());
            throw new UnauthorizedException("Refresh token has expired");
        }

        // User-Agent validation (ADR Decision 2: strict rejection)
        String incomingUaHash = jwtService.sha256(userAgent != null ? userAgent : "");
        if (!incomingUaHash.equals(token.getUserAgentHash())) {
            log.warn("[SECURITY_ALERT: USER_AGENT_MISMATCH] User-Agent mismatch for token id: {}, user: {}",
                    token.getId(), token.getUser().getId());
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            throw new UnauthorizedException("User-Agent mismatch");
        }

        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("[AUTH_FAILURE: INACTIVE_USER] Refresh attempt for inactive user: {}", user.getId());
            throw new UnauthorizedException("User account is inactive or suspended");
        }

        // Issue new refresh token & rotate
        String newRawRefreshToken = jwtService.generateRefreshToken();
        String newTokenHash = jwtService.sha256(newRawRefreshToken);
        Instant expiresAt = Instant.now().plus(jwtProperties.getRefreshTokenTtlSeconds(), ChronoUnit.SECONDS);

        RefreshToken newRefreshToken = new RefreshToken(user, newTokenHash, expiresAt, incomingUaHash);
        RefreshToken savedNewToken = refreshTokenRepository.save(newRefreshToken);

        token.setRevokedAt(Instant.now());
        token.setReplacedById(savedNewToken.getId());
        refreshTokenRepository.save(token);

        String accessToken = jwtService.generateAccessToken(user);
        UserSummaryDto userSummary = toSummary(user);
        AuthResponse authResponse = new AuthResponse(accessToken, jwtProperties.getAccessTokenTtlSeconds(), userSummary);
        ResponseCookie cookie = buildRefreshCookie(newRawRefreshToken, jwtProperties.getRefreshTokenTtlSeconds());

        log.info("Refresh token rotated successfully for user: {}", user.getId());
        return new AuthSession(authResponse, cookie);
    }

    /**
     * Traverses the replaced_by_id chain downstream and revokes all descendants to contain token theft.
     */
    private void revokeTokenFamily(RefreshToken token) {
        Instant now = Instant.now();
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
        }

        UUID nextId = token.getReplacedById();
        while (nextId != null) {
            RefreshToken descendant = refreshTokenRepository.findById(nextId).orElse(null);
            if (descendant == null) {
                break;
            }
            if (descendant.getRevokedAt() == null) {
                descendant.setRevokedAt(now);
                refreshTokenRepository.save(descendant);
                log.info("Revoked descendant token in family: tokenId={}", descendant.getId());
            }
            nextId = descendant.getReplacedById();
        }
    }

    /**
     * Revokes the refresh token and returns a clearing cookie (Max-Age=0).
     */
    @Transactional
    public ResponseCookie logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = jwtService.sha256(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                if (token.getRevokedAt() == null) {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                    log.info("Refresh token revoked on logout: tokenId={}", token.getId());
                }
            });
        }

        return buildRefreshCookie("", 0);
    }

    private ResponseCookie buildRefreshCookie(String tokenValue, long maxAge) {
        return ResponseCookie.from("refreshToken", tokenValue)
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
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
