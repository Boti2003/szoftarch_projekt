package hu.bme.szoftarch.social.domain.port;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound port used to fetch public profile statistics from the Identity
 * service in batches.
 *
 * <p>Implemented by an infrastructure adapter backed by Identity's internal
 * API ({@code POST /internal/v1/users/public-stats:batch}). The adapter is
 * responsible for chunking requests to at most 100 user ids per call; this
 * domain contract accepts the full set and returns the aggregated result.
 * Pure domain contract with no Spring or JPA dependency.
 */
public interface IdentityStatsPort {

    /**
     * Fetches public stats for the given user ids.
     *
     * <p>The returned map is keyed by user id. Ids that have no corresponding
     * stats (e.g. an unknown user) may be absent from the result.
     *
     * @param userIds the user ids to look up; must not be {@code null}
     * @return a map from user id to its {@link PublicStats}; never {@code null}
     */
    Map<UUID, PublicStats> batchStats(Set<UUID> userIds);
}
