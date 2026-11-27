package hu.bme.szoftarch.social.application.leaderboard;

import java.util.UUID;

/**
 * Application-layer read model for a single row of the friends leaderboard
 * (design "Domain Types").
 *
 * <p>Social does not persist points or profile data; {@code displayName},
 * {@code points}, and {@code level} come from Identity's batch stats API. When
 * Identity has no record for an id (a friend or the user themselves), the entry
 * is still included with {@code points = 0}, {@code level = 0}, and a
 * {@code null} display name so the ranking remains complete (requirement 7.1).
 *
 * @param userId      the entry's user id
 * @param displayName the user's public display name, or {@code null} if unknown
 * @param points      the user's profile points (0 when unknown)
 * @param level       the user's level (0 when unknown)
 * @param rank        the 1-based position in the leaderboard (points descending,
 *                    stable tie-break on {@code userId})
 */
public record LeaderboardEntryView(
        UUID userId,
        String displayName,
        long points,
        int level,
        int rank) {
}
