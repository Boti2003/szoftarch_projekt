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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WireMock tests for {@link IdentityStatsAdapter} exercising the real
 * {@link IdentityHttpClientConfig} transport (request factory with the mandated
 * timeouts + the header-stamping interceptor). The adapter is wired against a
 * live WireMock server instead of a full Spring context so the test stays light
 * while still driving the real {@link RestClient} interceptor and timeout path.
 *
 * <p>Covers task 6.2: batch chunking at &le;100 ids, the
 * {@code X-Internal-Api-Key}/{@code X-Correlation-Id} headers, the 2s/5s timeout
 * configuration with a single retry on idempotent reads, and the
 * {@code exists} 200/404 mapping.
 */
class IdentityStatsAdapterWireMockTest {

    private static final String API_KEY = "test-internal-key-123";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String EXISTS_PATH = "/internal/v1/users/([0-9a-fA-F-]+)/exists";
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

    /**
     * Builds the adapter via the real config beans, pointing the Identity base
     * URL at the running WireMock server. {@code readTimeout} is parameterised
     * so timeout tests can stay fast while still exercising the real
     * timeout-then-retry path; functional tests use the mandated 2s/5s values.
     */
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

    /** Adapter with the production-mandated 2s connect / 5s read timeouts. */
    private IdentityStatsAdapter newAdapter() {
        return newAdapter(Duration.ofSeconds(2), Duration.ofSeconds(5), 100);
    }

    // ----------------------------------------------------------------------
    // 1. batchStats chunking at the 100-id boundary + result merge
    // ----------------------------------------------------------------------

    @Test
    void batchStats_with_more_than_100_ids_chunks_into_multiple_calls_and_merges() {
        // 150 ids -> two chunks of 100 + 50 (batchSize 100).
        Set<UUID> ids = new LinkedHashSet<>();
        for (int i = 0; i < 150; i++) {
            ids.add(UUID.randomUUID());
        }

        // Build an explicit JSON array keyed to the exact ids, split into the two
        // chunks the adapter is expected to produce (first 100 ids, then 50).
        java.util.List<UUID> ordered = new java.util.ArrayList<>(ids);
        String firstChunkBody = statsArray(ordered.subList(0, 100));
        String secondChunkBody = statsArray(ordered.subList(100, 150));

        // Match the first chunk by its exact userIds payload and the second by
        // the complementary one, so we assert chunk boundaries precisely.
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .withRequestBody(matching(".*" + ordered.get(0) + ".*" + ordered.get(99) + ".*"))
                .willReturn(json200(firstChunkBody)));
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .withRequestBody(matching(".*" + ordered.get(100) + ".*" + ordered.get(149) + ".*"))
                .willReturn(json200(secondChunkBody)));

        Map<UUID, PublicStats> result = newAdapter().batchStats(ids);

        // Exactly two POST calls (one per chunk).
        wireMock.verify(2, postRequestedFor(urlPathEqualTo(BATCH_PATH)));

        // Every POST body carried at most 100 ids.
        wireMock.getAllServeEvents().forEach(event -> {
            String body = event.getRequest().getBodyAsString();
            if (body != null && body.contains("userIds")) {
                long idCount = body.chars().filter(c -> c == '-').count() / 4; // 4 dashes per UUID
                assertThat(idCount).isLessThanOrEqualTo(100);
            }
        });

        // Merge covered every id.
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
        UUID id = UUID.randomUUID();
        String body = "[{\"userId\":\"" + id + "\",\"displayName\":\"Alice\",\"points\":42,\"level\":3}]";
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH)).willReturn(json200(body)));

        Map<UUID, PublicStats> result = newAdapter().batchStats(Set.of(id));

        assertThat(result).containsKey(id);
        PublicStats stats = result.get(id);
        assertThat(stats.displayName()).isEqualTo("Alice");
        assertThat(stats.points()).isEqualTo(42L);
        assertThat(stats.level()).isEqualTo(3);
    }

    // ----------------------------------------------------------------------
    // 2. internal headers on every request
    // ----------------------------------------------------------------------

    @Test
    void every_request_carries_api_key_and_non_empty_correlation_id() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .willReturn(json200("{\"exists\":true}")));
        wireMock.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(json200("[]")));

        IdentityStatsAdapter adapter = newAdapter();
        adapter.exists(id);
        adapter.batchStats(Set.of(UUID.randomUUID()));

        wireMock.verify(getRequestedFor(urlPathMatching(EXISTS_PATH))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withHeader(CORRELATION_HEADER, matching(".+")));
        wireMock.verify(postRequestedFor(urlPathEqualTo(BATCH_PATH))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withHeader(CORRELATION_HEADER, matching(".+")));
    }

    @Test
    void correlation_id_from_mdc_is_propagated_when_present() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .willReturn(json200("{\"exists\":true}")));

        String correlationId = UUID.randomUUID().toString();
        org.slf4j.MDC.put("correlationId", correlationId);
        try {
            newAdapter().exists(id);
        } finally {
            org.slf4j.MDC.remove("correlationId");
        }

        wireMock.verify(getRequestedFor(urlPathMatching(EXISTS_PATH))
                .withHeader(CORRELATION_HEADER, equalTo(correlationId)));
    }

    // ----------------------------------------------------------------------
    // 3. timeout + retry behaviour
    // ----------------------------------------------------------------------

    @Test
    void read_timeout_triggers_exactly_one_retry_then_integration_exception() {
        UUID id = UUID.randomUUID();
        // Delay well past the (lowered) read timeout so every attempt times out.
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"exists\":true}")));

        // Short read timeout keeps the test fast; the retry-once path is identical
        // to the production 5s timeout, just quicker to exercise.
        IdentityStatsAdapter adapter = newAdapter(Duration.ofSeconds(2), Duration.ofMillis(400), 100);

        assertThatThrownBy(() -> adapter.exists(id))
                .isInstanceOf(IntegrationException.class);

        // Exactly two attempts: initial + one retry.
        wireMock.verify(2, getRequestedFor(urlPathMatching(EXISTS_PATH)));
    }

    @Test
    void five_hundred_then_two_hundred_succeeds_on_the_retry_with_exactly_two_attempts() {
        UUID id = UUID.randomUUID();
        // First response 503, second response 200 -> succeeds on retry.
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .inScenario("retry-on-5xx")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .inScenario("retry-on-5xx")
                .whenScenarioStateIs("recovered")
                .willReturn(json200("{\"exists\":true}")));

        boolean exists = newAdapter().exists(id);

        assertThat(exists).isTrue();
        wireMock.verify(2, getRequestedFor(urlPathMatching(EXISTS_PATH)));
    }

    @Test
    void persistent_5xx_exhausts_retry_budget_and_raises_integration_exception() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> newAdapter().exists(id))
                .isInstanceOf(IntegrationException.class);

        // Initial + single retry, then give up.
        wireMock.verify(2, getRequestedFor(urlPathMatching(EXISTS_PATH)));
    }

    @Test
    void integration_exception_message_never_leaks_the_api_key() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> newAdapter().exists(id))
                .isInstanceOf(IntegrationException.class)
                .hasMessageNotContaining(API_KEY);
    }

    // ----------------------------------------------------------------------
    // 4. exists mapping: 200/{"exists":true} -> true, 404 -> false
    // ----------------------------------------------------------------------

    @Test
    void exists_maps_200_true_to_true() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .willReturn(json200("{\"exists\":true}")));

        assertThat(newAdapter().exists(id)).isTrue();
    }

    @Test
    void exists_maps_200_false_to_false() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .willReturn(json200("{\"exists\":false}")));

        assertThat(newAdapter().exists(id)).isFalse();
    }

    @Test
    void exists_maps_404_to_false() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(get(urlPathMatching(EXISTS_PATH))
                .willReturn(aResponse().withStatus(404)));

        assertThat(newAdapter().exists(id)).isFalse();
        // 404 is a definitive answer, not a transient failure: no retry.
        wireMock.verify(1, getRequestedFor(urlPathMatching(EXISTS_PATH)));
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

    /** Builds a JSON stats array with one row per id. */
    private static String statsArray(java.util.List<UUID> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"userId\":\"").append(ids.get(i))
                    .append("\",\"displayName\":\"u").append(i)
                    .append("\",\"points\":").append(i)
                    .append(",\"level\":1}");
        }
        return sb.append(']').toString();
    }
}
