package hu.bme.szoftarch.social.infrastructure.identity;

import com.github.tomakehurst.wiremock.WireMockServer;
import hu.bme.szoftarch.social.domain.port.PublicStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WireMock tests for {@link IdentityStatsAdapter} exercising the real
 * {@link IdentityHttpClientConfig} transport (request factory with the mandated
 * timeouts + the header-stamping interceptor). The adapter is wired against a
 * live WireMock server instead of a full Spring context.
 *
 * <p>Both ports funnel through Identity's batch public-stats endpoint: Identity
 * exposes no dedicated existence endpoint, so {@code exists} is derived from a
 * single-id batch call (a user exists iff a stats row is returned). Covers batch
 * chunking at &le;100 ids, the {@code X-Internal-Api-Key}/{@code X-Correlation-Id}
 * headers, the timeout-then-retry path, and the response field mapping
 * ({@code id}->userId, {@code totalPoints}->points).
 */
class IdentityStatsAdapterWireMockTest {

    private static final String API_KEY = "test-internal-key-123";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String BATCH_PATH = "/internal/v1/users/public-stats:batch";

    private WireMockServer wireMock;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    private IdentityStatsAdapter newAdapter(Duration connectTimeout, Duration readTimeout, int batchSize) {
        IdentityClientProperties properties = new IdentityClientProperties(
                wireMock.baseUrl(),
                API_KEY,
                CORRELATION_HEADER,
                connectTimeout,
                readTimeout,
                batchSize);
        IdentityHttpClientConfig config = new IdentityHttpClientConfig();
        ClientHttpRequestFactory factory = config.identityClientHttpRequestFactory(properties);
        ClientHttpRequestInterceptor interceptor =
                config.identityInternalHeadersInterceptor(properties, new CorrelationIdAccessor());
        RestClient restClient = config.identityRestClient(properties, factory, interceptor);
        return new IdentityStatsAdapter(restClient, properties);
    }

    private IdentityStatsAdapter newAdapter() {
        return newAdapter(Duration.ofSeconds(2), Duration.ofSeconds(5), 100);
    }

    // ----------------------------------------------------------------------
    // 1. batchStats chunking at the 100-id boundary + result merge
    // ----------------------------------------------------------------------

    @Test
    void batchStats_with_more_than_100_ids_chunks_into_multiple_calls_and_merges() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (int i = 0; i < 150; i++) {
            ids.add(UUID.randomUUID());
        }
        java.util.List<UUID> ordered = new java.util.ArrayList<>(ids);
        String firstChunkBody = statsArray(ordered.subList(0, 100));
        String secondChunkBody = statsArray(ordered.subList(100, 150));

        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .withRequestBody(matching(".*" + ordered.get(0) + ".*" + ordered.get(99) + ".*"))
                .willReturn(json200(firstChunkBody)));
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .withRequestBody(matching(".*" + ordered.get(100) + ".*" + ordered.get(149) + ".*"))
                .willReturn(json200(secondChunkBody)));

        Map<UUID, PublicStats> result = newAdapter().batchStats(ids);

        wireMock.verify(2, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
        wireMock.getAllServeEvents().forEach(event -> {
            String body = event.getRequest().getBodyAsString();
            if (body != null && body.contains("userIds")) {
                long idCount = body.chars().filter(c -> c == '-').count() / 4;
                assertThat(idCount).isLessThanOrEqualTo(100);
            }
        });
        assertThat(result).hasSize(150);
        assertThat(result.keySet()).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void batchStats_with_exactly_100_ids_issues_a_single_call() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(UUID.randomUUID());
        }
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(json200(statsArray(new java.util.ArrayList<>(ids)))));

        Map<UUID, PublicStats> result = newAdapter().batchStats(ids);

        wireMock.verify(1, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
        assertThat(result).hasSize(100);
    }

    @Test
    void batchStats_maps_response_rows_to_public_stats() {
        // Identity returns PublicProfileDto: id/username/displayName/level/totalPoints.
        UUID id = UUID.randomUUID();
        String body = "[{\"id\":\"" + id + "\",\"username\":\"alice\",\"displayName\":\"Alice\","
                + "\"level\":3,\"totalPoints\":42}]";
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH)).willReturn(json200(body)));

        Map<UUID, PublicStats> result = newAdapter().batchStats(Set.of(id));

        assertThat(result).containsKey(id);
        PublicStats stats = result.get(id);
        assertThat(stats.displayName()).isEqualTo("Alice");
        assertThat(stats.points()).isEqualTo(42L);
        assertThat(stats.level()).isEqualTo(3);
    }

    // ----------------------------------------------------------------------
    // 2. internal headers on every request (via the batch call)
    // ----------------------------------------------------------------------

    @Test
    void every_request_carries_api_key_and_non_empty_correlation_id() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH)).willReturn(json200("[]")));

        newAdapter().batchStats(Set.of(UUID.randomUUID()));

        wireMock.verify(postRequestedFor(urlPathEqualTo(BATCH_PATH))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withHeader(CORRELATION_HEADER, matching(".+")));
    }

    @Test
    void correlation_id_from_mdc_is_propagated_when_present() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH)).willReturn(json200("[]")));

        String correlationId = UUID.randomUUID().toString();
        org.slf4j.MDC.put("correlationId", correlationId);
        try {
            newAdapter().batchStats(Set.of(UUID.randomUUID()));
        } finally {
            org.slf4j.MDC.remove("correlationId");
        }

        wireMock.verify(postRequestedFor(urlPathEqualTo(BATCH_PATH))
                .withHeader(CORRELATION_HEADER, equalTo(correlationId)));
    }

    // ----------------------------------------------------------------------
    // 3. timeout + retry behaviour (idempotent read -> at most one retry)
    // ----------------------------------------------------------------------

    @Test
    void read_timeout_triggers_exactly_one_retry_then_integration_exception() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        IdentityStatsAdapter adapter = newAdapter(Duration.ofSeconds(2), Duration.ofMillis(400), 100);

        assertThatThrownBy(() -> adapter.batchStats(Set.of(UUID.randomUUID())))
                .isInstanceOf(IntegrationException.class);

        wireMock.verify(2, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
    }

    @Test
    void five_hundred_then_two_hundred_succeeds_on_the_retry_with_exactly_two_attempts() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .inScenario("retry-on-5xx")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .inScenario("retry-on-5xx")
                .whenScenarioStateIs("recovered")
                .willReturn(json200("[]")));

        Map<UUID, PublicStats> result = newAdapter().batchStats(Set.of(UUID.randomUUID()));

        assertThat(result).isEmpty();
        wireMock.verify(2, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
    }

    @Test
    void persistent_5xx_exhausts_retry_budget_and_raises_integration_exception() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> newAdapter().batchStats(Set.of(UUID.randomUUID())))
                .isInstanceOf(IntegrationException.class);

        wireMock.verify(2, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
    }

    @Test
    void integration_exception_message_never_leaks_the_api_key() {
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> newAdapter().batchStats(Set.of(UUID.randomUUID())))
                .isInstanceOf(IntegrationException.class)
                .hasMessageNotContaining(API_KEY);
    }

    // ----------------------------------------------------------------------
    // 4. exists derived from the batch call: row present -> true, absent -> false
    // ----------------------------------------------------------------------

    @Test
    void exists_true_when_batch_returns_a_row_for_the_id() {
        UUID id = UUID.randomUUID();
        String body = "[{\"id\":\"" + id + "\",\"username\":\"u\",\"displayName\":\"U\","
                + "\"level\":1,\"totalPoints\":0}]";
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH)).willReturn(json200(body)));

        assertThat(newAdapter().exists(id)).isTrue();
        wireMock.verify(1, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
    }

    @Test
    void exists_false_when_batch_returns_no_row_for_the_id() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH)).willReturn(json200("[]")));

        assertThat(newAdapter().exists(id)).isFalse();
    }

    // ----------------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------------

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json200(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    /** Builds a JSON stats array (PublicProfileDto shape) with one row per id. */
    private static String statsArray(java.util.List<UUID> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(ids.get(i))
                    .append("\",\"username\":\"u").append(i)
                    .append("\",\"displayName\":\"u").append(i)
                    .append("\",\"level\":1,\"totalPoints\":").append(i)
                    .append('}');
        }
        return sb.append(']').toString();
    }
}
