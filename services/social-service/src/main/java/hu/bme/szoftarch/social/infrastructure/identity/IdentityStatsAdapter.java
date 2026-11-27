package hu.bme.szoftarch.social.infrastructure.identity;

import hu.bme.szoftarch.social.domain.port.IdentityStatsPort;
import hu.bme.szoftarch.social.domain.port.PublicStats;
import hu.bme.szoftarch.social.domain.port.UserExistencePort;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adapter over Identity's internal API implementing the {@link UserExistencePort}
 * and {@link IdentityStatsPort} outbound ports.
 *
 * <ul>
 *   <li>{@code exists} &rarr; derived from the batch public-stats call
 *       (Identity exposes no dedicated existence endpoint).</li>
 *   <li>{@code batchStats} &rarr; {@code POST /internal/v1/users/public-stats:batch},
 *       chunked to at most {@code batchSize} (&le; 100) ids per call, results merged.</li>
 * </ul>
 *
 * <p>Both operations are idempotent reads, so each HTTP call is attempted at
 * most twice: one retry on a connect/read timeout or a 5xx response. After the
 * budget is exhausted an {@link IntegrationException} is raised. The
 * {@code X-Internal-Api-Key} and {@code X-Correlation-Id} headers are added by
 * the shared request interceptor ({@link IdentityHttpClientConfig}); the api key
 * is never logged here.
 *
 * <p><strong>Contract assumption for {@code exists}:</strong> Identity returns
 * {@code 200} with a JSON body {@code {"exists": true|false}} when it can answer
 * authoritatively. To be robust against a REST-style variant where a missing
 * user yields {@code 404}, a {@code 404} is also mapped to {@code false}. A
 * {@code 200} with no/garbled body is treated as {@code true} (the user id was
 * resolvable). Any other 4xx propagates as a client error.
 *
 * <p><strong>Contract assumption for {@code batchStats}:</strong> the request
 * body is {@code {"userIds": [uuid, ...]}} and the response is a JSON array of
 * {@code {userId, displayName, points, level}} objects; unknown ids are simply
 * absent from the array.
 */
@Component
@Profile("!dev") // In dev, FakeIdentityAdapter stands in so the service runs without Identity.
public class IdentityStatsAdapter implements UserExistencePort, IdentityStatsPort {

    private static final Logger log = LoggerFactory.getLogger(IdentityStatsAdapter.class);

    private static final ParameterizedTypeReference<List<PublicStatsResponse>> STATS_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final int batchSize;

    public IdentityStatsAdapter(RestClient identityRestClient, IdentityClientProperties properties) {
        this.restClient = identityRestClient;
        this.batchSize = properties.batchSize();
    }

    @Override
    public boolean exists(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        // Identity has no dedicated existence endpoint; a user exists iff the
        // batch public-stats call returns a row for the id.
        return batchStats(Set.of(userId)).containsKey(userId);
    }

    @Override
    public Map<UUID, PublicStats> batchStats(Set<UUID> userIds) {
        if (userIds == null) {
            throw new IllegalArgumentException("userIds must not be null");
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PublicStats> merged = new LinkedHashMap<>();
        for (List<UUID> chunk : chunk(userIds, batchSize)) {
            List<PublicStatsResponse> response = callIdempotent(
                    "POST /internal/v1/users/public-stats:batch",
                    () -> restClient.post()
                            .uri("/internal/v1/users/public-stats:batch")
                            .body(new BatchStatsRequest(chunk))
                            .retrieve()
                            .body(STATS_LIST));
            if (response != null) {
                for (PublicStatsResponse s : response) {
                    if (s != null && s.userId() != null) {
                        merged.put(s.userId(),
                                new PublicStats(s.userId(), s.displayName(), s.points(), s.level()));
                    }
                }
            }
        }
        return merged;
    }

    /**
     * Splits the given ids into ordered chunks of at most {@code size} entries.
     * The iteration order of {@code userIds} is preserved so chunking is stable
     * and testable at the 100-id boundary.
     */
    static List<List<UUID>> chunk(Set<UUID> userIds, int size) {
        List<UUID> all = new ArrayList<>(userIds);
        List<List<UUID>> chunks = new ArrayList<>();
        for (int i = 0; i < all.size(); i += size) {
            chunks.add(new ArrayList<>(all.subList(i, Math.min(i + size, all.size()))));
        }
        return chunks;
    }

    /**
     * Executes an idempotent read with at most one retry on timeout or 5xx.
     * After the retry budget is exhausted an {@link IntegrationException} is
     * raised; the cause carries the transport/HTTP failure but no api key.
     */
    private <T> T callIdempotent(String operation, Supplier<T> call) {
        int attempts = 2; // idempotent read -> at most one retry
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return call.get();
            } catch (HttpClientErrorException e) {
                // 4xx: a definitive client answer, not a transient failure. Do not
                // retry; surface it immediately as an integration failure.
                throw new IntegrationException(
                        "Identity call failed (client error): " + operation, e);
            } catch (RestClientException e) {
                // Retryable transport/upstream failure: connect/read timeout
                // (ResourceAccessException), 5xx (HttpServerErrorException), or a
                // bare RestClientException raised while reading the response.
                last = e;
            }
            if (attempt < attempts) {
                log.warn("Retrying idempotent Identity call {} after transient failure ({})",
                        operation, describe(last));
            }
        }
        throw new IntegrationException(
                "Identity call failed after retry budget: " + operation, last);
    }

    private static String describe(RuntimeException e) {
        if (e instanceof HttpServerErrorException httpError) {
            HttpStatusCode status = httpError.getStatusCode();
            return "HTTP " + status.value();
        }
        return e.getClass().getSimpleName();
    }

    /** Request body for the public-stats batch call. */
    record BatchStatsRequest(List<UUID> userIds) {
    }

    /**
     * Response row for one user's public stats. Identity returns PublicProfileDto
     * ({id, username, displayName, level, totalPoints}); map id->userId and
     * totalPoints->points explicitly.
     */
    record PublicStatsResponse(
            @JsonProperty("id") UUID userId,
            String displayName,
            @JsonProperty("totalPoints") long points,
            int level) {
    }
}
