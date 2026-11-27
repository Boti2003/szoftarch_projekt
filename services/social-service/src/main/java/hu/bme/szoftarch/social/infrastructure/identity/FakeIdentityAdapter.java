package hu.bme.szoftarch.social.infrastructure.identity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import hu.bme.szoftarch.social.domain.port.IdentityStatsPort;
import hu.bme.szoftarch.social.domain.port.PublicStats;
import hu.bme.szoftarch.social.domain.port.UserExistencePort;

/**
 * <strong>DEVELOPMENT / DEMO ONLY</strong> stand-in for the Identity outbound
 * ports, active only under the {@code dev} Spring profile.
 *
 * <p>When social-service runs on its own (no Identity service on the network),
 * the real {@link IdentityStatsAdapter} (gated to {@code @Profile("!dev")}) would
 * fail with a connection error on every call. This fake lets the full social API
 * be exercised in isolation by returning deterministic, locally-computed answers
 * instead of calling Identity:
 *
 * <ul>
 *   <li>{@link #exists(UUID)} always returns {@code true}, so friend-request
 *       creation and duel creation pass their "receiver/opponent exists" check.</li>
 *   <li>{@link #batchStats(Set)} returns a placeholder {@link PublicStats} for
 *       every requested id (derived deterministically from the id) so the friends
 *       list and leaderboard render with stable demo data.</li>
 * </ul>
 *
 * <p><strong>Never enable the {@code dev} profile in production:</strong> this
 * fabricates identity data and performs no real verification.
 */
@Component
@Profile("dev")
public class FakeIdentityAdapter implements UserExistencePort, IdentityStatsPort {

    @Override
    public boolean exists(UUID userId) {
        // In dev every referenced user is treated as existing.
        return userId != null;
    }

    @Override
    public Map<UUID, PublicStats> batchStats(Set<UUID> userIds) {
        Map<UUID, PublicStats> result = new LinkedHashMap<>();
        if (userIds == null) {
            return result;
        }
        for (UUID id : userIds) {
            if (id == null) {
                continue;
            }
            // Deterministic placeholder data so repeated calls are stable.
            String shortId = id.toString().substring(0, 8);
            long points = Math.floorMod(id.hashCode(), 1000);
            int level = (int) (points / 100) + 1;
            result.put(id, new PublicStats(id, "dev-user-" + shortId, points, level));
        }
        return result;
    }
}
