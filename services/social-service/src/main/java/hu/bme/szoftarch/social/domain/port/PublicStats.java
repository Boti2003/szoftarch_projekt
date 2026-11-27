package hu.bme.szoftarch.social.domain.port;

import java.util.UUID;

/**
 * Public profile statistics for a user, owned by the Identity service and
 * fetched on demand (Social never persists points or profile data).
 *
 * <p>Returned by {@link IdentityStatsPort#batchStats} and used to build the
 * friends leaderboard (design "Domain Types").
 *
 * @param userId      the user's id
 * @param displayName the user's public display name
 * @param points      the user's profile points
 * @param level       the user's level
 */
public record PublicStats(UUID userId, String displayName, long points, int level) {
}
