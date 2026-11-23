package hu.bme.szoftarch.identity.application;

import hu.bme.szoftarch.identity.api.dto.PublicProfileDto;
import hu.bme.szoftarch.identity.api.dto.UpdateProfileRequest;
import hu.bme.szoftarch.identity.api.dto.UserProfileResponse;
import hu.bme.szoftarch.identity.application.exception.ConflictException;
import hu.bme.szoftarch.identity.application.exception.NotFoundException;
import hu.bme.szoftarch.identity.domain.User;
import hu.bme.szoftarch.identity.domain.UserStats;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    public UserService(UserRepository userRepository, UserStatsRepository userStatsRepository) {
        this.userRepository = userRepository;
        this.userStatsRepository = userStatsRepository;
    }

    /**
     * Retrieves the profile and game statistics for the authenticated user.
     * Calculates the point threshold for the next level: nextLevelPoints = level^2 * 50.
     */
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        UserStats stats = user.getStats() != null ? user.getStats() :
                userStatsRepository.findById(userId).orElseGet(() -> new UserStats(user));

        long nextLevelPoints = (long) Math.pow(stats.getLevel(), 2) * 50;

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                stats.getTotalPoints(),
                stats.getLevel(),
                nextLevelPoints,
                user.getCreatedAt()
        );
    }

    /**
     * Updates display name and/or username.
     * Ensures username uniqueness if modified.
     */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        if (request.username() != null && !request.username().isBlank()) {
            String newUsername = request.username().trim().toLowerCase();
            if (!newUsername.equalsIgnoreCase(user.getUsername())) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw new ConflictException("Username is already taken: " + newUsername);
                }
                user.setUsername(newUsername);
            }
        }

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }

        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);
        log.info("Profile updated for user: userId={}, username={}", saved.getId(), saved.getUsername());

        return getProfile(saved.getId());
    }

    /**
     * Searches users by prefix on username, returning up to 20 results.
     */
    public List<PublicProfileDto> searchUsers(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<User> users = userRepository.searchByUsernamePrefix(query.trim());

        return users.stream()
                .limit(20)
                .map(u -> {
                    UserStats stats = u.getStats() != null ? u.getStats() :
                            userStatsRepository.findById(u.getId()).orElse(null);
                    int level = stats != null ? stats.getLevel() : 1;
                    long points = stats != null ? stats.getTotalPoints() : 0L;
                    return new PublicProfileDto(u.getId(), u.getUsername(), u.getDisplayName(), level, points);
                })
                .toList();
    }

    /**
     * Retrieves public profile and statistics for a given user ID.
     */
    public PublicProfileDto getPublicProfile(UUID targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found: " + targetUserId));

        UserStats stats = user.getStats() != null ? user.getStats() :
                userStatsRepository.findById(targetUserId).orElseGet(() -> new UserStats(user));

        return new PublicProfileDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                stats.getLevel(),
                stats.getTotalPoints()
        );
    }
}
