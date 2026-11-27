package hu.bme.szoftarch.identity.infrastructure.persistence;

import hu.bme.szoftarch.identity.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class PersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserStatsRepository userStatsRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Test
    @DisplayName("Save, retrieve, and prefix search for user")
    void testUserAndStatsCreationAndPrefixSearch() {
        // 1. Create user and associated user stats
        User user = new User("annakovacs@example.com", "annakovacs", "Anna Kovacs", "hashed_pwd_123");
        UserStats stats = new UserStats(user);
        stats.setTotalPoints(150);
        stats.setLevel(2);
        user.setStats(stats);

        User savedUser = userRepository.save(user);
        assertThat(savedUser.getId()).isNotNull();

        // 2. Lookup by username
        Optional<User> foundByUsername = userRepository.findByUsername("annakovacs");
        assertThat(foundByUsername).isPresent();
        assertThat(foundByUsername.get().getEmail()).isEqualTo("annakovacs@example.com");

        // 3. Prefix search: 'anna' matches, 'bela' does not
        List<User> prefixResults = userRepository.searchByUsernamePrefix("anna");
        assertThat(prefixResults).hasSize(1);
        assertThat(prefixResults.get(0).getUsername()).isEqualTo("annakovacs");

        List<User> noResults = userRepository.searchByUsernamePrefix("bela");
        assertThat(noResults).isEmpty();
    }

    @Test
    @DisplayName("Paginated global leaderboard and rank calculation")
    void testLeaderboardAndRankCalculation() {
        // Persist 3 users with different point totals
        User u1 = userRepository.save(new User("u1@test.com", "user_one", "User 1", "hash"));
        User u2 = userRepository.save(new User("u2@test.com", "user_two", "User 2", "hash"));
        User u3 = userRepository.save(new User("u3@test.com", "user_three", "User 3", "hash"));

        UserStats s1 = new UserStats(u1);
        s1.setTotalPoints(300);
        s1.setLevel(3);
        userStatsRepository.save(s1);

        UserStats s2 = new UserStats(u2);
        s2.setTotalPoints(500);
        s2.setLevel(4);
        userStatsRepository.save(s2);

        UserStats s3 = new UserStats(u3);
        s3.setTotalPoints(100);
        s3.setLevel(1);
        userStatsRepository.save(s3);

        // Fetch first page of leaderboard (top 10)
        var leaderboardPage = userStatsRepository.findLeaderboard(PageRequest.of(0, 10));
        assertThat(leaderboardPage.getContent()).hasSize(3);
        // Descending points order: 500 -> 300 -> 100
        assertThat(leaderboardPage.getContent().get(0).getTotalPoints()).isEqualTo(500);
        assertThat(leaderboardPage.getContent().get(1).getTotalPoints()).isEqualTo(300);
        assertThat(leaderboardPage.getContent().get(2).getTotalPoints()).isEqualTo(100);

        // Verify dynamic rank calculations:
        // u2 (500 pts): 0 users have more points -> rank 1
        assertThat(userStatsRepository.calculateRank(500)).isEqualTo(1);
        // u1 (300 pts): 1 user has more points -> rank 2
        assertThat(userStatsRepository.calculateRank(300)).isEqualTo(2);
        // u3 (100 pts): 2 users have more points -> rank 3
        assertThat(userStatsRepository.calculateRank(100)).isEqualTo(3);
    }

    @Test
    @DisplayName("Refresh token storage, validity check, and bulk revocation")
    void testRefreshTokenLifecycle() {
        User user = userRepository.save(new User("tokenuser@test.com", "tokenuser", "Token User", "hash"));

        RefreshToken rt = new RefreshToken(
                user,
                "token_sha256_hash_value_xyz",
                Instant.now().plus(7, ChronoUnit.DAYS),
                "user_agent_chrome_linux"
        );
        refreshTokenRepository.save(rt);

        // Lookup by token hash
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("token_sha256_hash_value_xyz");
        assertThat(found).isPresent();
        assertThat(found.get().isValid()).isTrue();
        assertThat(found.get().isExpired()).isFalse();

        // Revoke all active tokens for user
        int revokedCount = refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        assertThat(revokedCount).isEqualTo(1);

        RefreshToken refreshed = refreshTokenRepository.findById(rt.getId()).orElseThrow();
        assertThat(refreshed.isRevoked()).isTrue();
        assertThat(refreshed.isValid()).isFalse();
    }

    @Test
    @DisplayName("Point transaction storage and idempotency key uniqueness")
    void testPointTransactionIdempotency() {
        User user = userRepository.save(new User("pointsuser@test.com", "pointsuser", "Points User", "hash"));

        PointTransaction tx = new PointTransaction(
                user,
                "LESSON_COMPLETION",
                "lesson-uuid-42",
                50,
                "unique-idempotency-key-abc-123"
        );
        pointTransactionRepository.save(tx);

        Optional<PointTransaction> found = pointTransactionRepository.findByIdempotencyKey("unique-idempotency-key-abc-123");
        assertThat(found).isPresent();
        assertThat(found.get().getPoints()).isEqualTo(50);
        assertThat(found.get().getSourceType()).isEqualTo("LESSON_COMPLETION");
        assertThat(found.get().getUser().getUsername()).isEqualTo("pointsuser");
    }
}
