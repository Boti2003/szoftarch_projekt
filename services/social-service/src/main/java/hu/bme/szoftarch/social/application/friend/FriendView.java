package hu.bme.szoftarch.social.application.friend;

import java.time.Instant;
import java.util.UUID;

/**
 * Application-layer read model for a single friend in the authenticated user's
 * friend list, aggregating the stored friendship with the friend's public
 * stats fetched from Identity.
 *
 * <p>Social does not persist points or profile data; {@code displayName},
 * {@code points}, and {@code level} come from Identity's batch stats API and
 * may be {@code null}/zero when Identity has no record for the id (the
 * friendship itself is still returned).
 *
 * @param userId       the friend's user id (the "other" member of the pair)
 * @param displayName  the friend's public display name, or {@code null} if unknown
 * @param points       the friend's profile points (0 when unknown)
 * @param level        the friend's level (0 when unknown)
 * @param friendsSince when the friendship was created (UTC)
 */
public record FriendView(
        UUID userId,
        String displayName,
        long points,
        int level,
        Instant friendsSince) {
}
