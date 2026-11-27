package hu.bme.szoftarch.social.infrastructure.learning;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import hu.bme.szoftarch.social.domain.duel.ScoreResult;
import hu.bme.szoftarch.social.domain.port.AnswerSubmission;
import hu.bme.szoftarch.social.domain.port.DuelSetRef;
import hu.bme.szoftarch.social.domain.port.ExerciseRef;

/**
 * WireMock tests for {@link LearningDuelAdapter}, exercising the real
 * {@link RestClient} (built by {@link LearningHttpClient}) against a stub
 * Learning internal API.
 *
 * <p>These tests verify the request/response contracts of the three
 * operations, the mandatory internal headers ({@code X-Internal-Api-Key},
 * {@code X-Correlation-Id}), {@code Idempotency-Key} forwarding, and
 * <b>Property 10 (Learning failure safety)</b>: on a timeout or repeated 5xx
 * that exhausts the retry budget the adapter raises {@link IntegrationException}
 * and never returns a (partial/fabricated) {@link ScoreResult}.
 *
 * <p>The client is wired directly with a WireMock base URL. A short read
 * timeout is configured so the timeout case stays fast while still driving the
 * production timeout path of the real {@code RestClient}.
 */
class LearningDuelAdapterWireMockTest {

    private static final String API_KEY = "super-secret-internal-key";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    /** Short read timeout keeps the timeout test fast while using the real client. */
    private static final Duration READ_TIMEOUT = Duration.ofMillis(400);

    @RegisterExtension
    static final WireMockExtension WIREMOCK = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private LearningDuelAdapter adapter;

    @BeforeEach
    void setUp() {
        // Reset any correlation id from a previous test so each call mints a fresh one.
        org.slf4j.MDC.clear();

        LearningClientProperties properties = new LearningClientProperties(
                WIREMOCK.baseUrl() + "/internal/v1",
                API_KEY,
                CORRELATION_HEADER,
                Duration.ofSeconds(2),
                READ_TIMEOUT);

        LearningHttpClient httpClient = new LearningHttpClient();
        ClientHttpRequestFactory factory = httpClient.learningClientRequestFactory(properties);
        RestClient restClient = httpClient.learningRestClient(properties, factory);

        adapter = new LearningDuelAdapter(restClient, properties, new CorrelationIdAccessor());
    }

    @Test
    void createDuelSet_postsRequestAndParsesResponse() {
        UUID languageId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"setId\":\"" + setId + "\",\"exerciseCount\":5}")));

        DuelSetRef ref = adapter.createDuelSet(languageId, 5);

        assertThat(ref.setId()).isEqualTo(setId);
        assertThat(ref.exerciseCount()).isEqualTo(5);

        WIREMOCK.verify(postRequestedFor(urlEqualTo("/internal/v1/duel-sets"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withHeader(CORRELATION_HEADER, matching(".+"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(equalToJson(
                        "{\"languageId\":\"" + languageId + "\",\"exerciseCount\":5}")));
    }

    @Test
    void fetchExercises_getsRequestAndPreservesOrder() {
        UUID setId = UUID.randomUUID();
        UUID ex1 = UUID.randomUUID();
        UUID ex2 = UUID.randomUUID();
        String body = "["
                + "{\"exerciseId\":\"" + ex1 + "\",\"prompt\":\"first\",\"options\":[\"a\",\"b\"],\"maxPoints\":10},"
                + "{\"exerciseId\":\"" + ex2 + "\",\"prompt\":\"second\",\"options\":[],\"maxPoints\":20}"
                + "]";
        WIREMOCK.stubFor(get(urlEqualTo("/internal/v1/duel-sets/" + setId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        List<ExerciseRef> exercises = adapter.fetchExercises(setId);

        assertThat(exercises).hasSize(2);
        // Order is preserved exactly as returned by Learning.
        assertThat(exercises.get(0).exerciseId()).isEqualTo(ex1);
        assertThat(exercises.get(0).prompt()).isEqualTo("first");
        assertThat(exercises.get(0).options()).containsExactly("a", "b");
        assertThat(exercises.get(0).maxPoints()).isEqualTo(10);
        assertThat(exercises.get(1).exerciseId()).isEqualTo(ex2);
        assertThat(exercises.get(1).options()).isEmpty();
        assertThat(exercises.get(1).maxPoints()).isEqualTo(20);

        WIREMOCK.verify(getRequestedFor(urlEqualTo("/internal/v1/duel-sets/" + setId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withHeader(CORRELATION_HEADER, matching(".+")));
    }

    @Test
    void scoreAnswer_postsAnswerAndForwardsIdempotencyKey() {
        UUID setId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        UUID idemKey = UUID.randomUUID();
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"correct\":true,\"awardedPoints\":10}")));

        ScoreResult result = adapter.scoreAnswer(setId, new AnswerSubmission(exerciseId, "answer-42"), idemKey);

        assertThat(result.correct()).isTrue();
        assertThat(result.awardedPoints()).isEqualTo(10);

        WIREMOCK.verify(postRequestedFor(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withHeader(CORRELATION_HEADER, matching(".+"))
                // The caller's Idempotency-Key is forwarded verbatim.
                .withHeader("Idempotency-Key", equalTo(idemKey.toString()))
                .withRequestBody(equalToJson(
                        "{\"exerciseId\":\"" + exerciseId + "\",\"answer\":\"answer-42\"}")));
    }

    /**
     * Validates: Requirements 5.1 / Property 10.
     *
     * <p>A transport-level failure (connection reset) on every attempt exhausts
     * the at-most-one-retry budget. The client raises a
     * {@link org.springframework.web.client.ResourceAccessException} that the
     * adapter treats as a retryable I/O failure, the same category as a
     * connect/read timeout where no response is produced. The adapter must raise
     * {@link IntegrationException} and must NOT return a {@link ScoreResult};
     * because the adapter is stateless, surfacing the failure (rather than a
     * partial/fabricated score) is what "no state advanced" means at this
     * boundary. Exactly two attempts are expected.
     *
     * <p>With the real {@code RestClient} / {@code SimpleClientHttpRequestFactory},
     * a transport/timeout failure that occurs while the response body is being
     * read can surface as a plain
     * {@link org.springframework.web.client.RestClientException} rather than a
     * {@link org.springframework.web.client.ResourceAccessException}.
     * {@link LearningDuelAdapter}'s {@code callWithRetry} classifies the whole
     * {@code RestClientException} hierarchy (except fast-fail 4xx) as a
     * retryable transport error, so this failure is retried once and then
     * wrapped as {@link IntegrationException}, upholding Property 10.
     */
    @Test
    void scoreAnswer_timeoutExhaustsRetryBudget_raisesIntegrationExceptionAndAdvancesNoState() {
        UUID setId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        UUID idemKey = UUID.randomUUID();
        // A dropped connection on every attempt is a transport I/O failure in the
        // same category as a connect/read timeout where no usable response is
        // produced; the adapter must retry once then raise IntegrationException.
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .willReturn(aResponse().withFault(
                        com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        AnswerSubmission submission = new AnswerSubmission(exerciseId, "answer-42");

        assertThatThrownBy(() -> adapter.scoreAnswer(setId, submission, idemKey))
                .isInstanceOf(IntegrationException.class);

        // Exactly two attempts: the original call plus one retry.
        WIREMOCK.verify(2, postRequestedFor(
                urlPathEqualTo("/internal/v1/duel-sets/" + setId + "/answers")));
    }

    /**
     * Validates: Requirements 5.1 / Property 10.
     *
     * <p>Repeated 5xx responses also exhaust the retry budget: the adapter
     * raises {@link IntegrationException} after exactly two attempts and never
     * returns a score.
     */
    @Test
    void scoreAnswer_repeated5xxExhaustsRetryBudget_raisesIntegrationExceptionAndAdvancesNoState() {
        UUID setId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        UUID idemKey = UUID.randomUUID();
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .willReturn(aResponse().withStatus(503)));

        AnswerSubmission submission = new AnswerSubmission(exerciseId, "answer-42");

        assertThatThrownBy(() -> adapter.scoreAnswer(setId, submission, idemKey))
                .isInstanceOf(IntegrationException.class);

        WIREMOCK.verify(2, postRequestedFor(
                urlPathEqualTo("/internal/v1/duel-sets/" + setId + "/answers")));
    }

    /**
     * A 5xx that recovers on the retry succeeds within the budget: this confirms
     * the single retry is actually exercised (two attempts, second one wins) and
     * that recovery returns the real parsed score rather than failing.
     */
    @Test
    void scoreAnswer_5xxThenSuccess_retriesOnceAndReturnsScore() {
        UUID setId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        UUID idemKey = UUID.randomUUID();
        String scenario = "retry-recovery";
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .inScenario(scenario)
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .inScenario(scenario)
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"correct\":false,\"awardedPoints\":0}")));

        ScoreResult result = adapter.scoreAnswer(setId, new AnswerSubmission(exerciseId, "answer-42"), idemKey);

        assertThat(result.correct()).isFalse();
        assertThat(result.awardedPoints()).isZero();
        WIREMOCK.verify(2, postRequestedFor(
                urlPathEqualTo("/internal/v1/duel-sets/" + setId + "/answers")));
    }
}
