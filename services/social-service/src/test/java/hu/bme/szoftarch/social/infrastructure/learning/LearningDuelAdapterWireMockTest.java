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
import java.util.Map;
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
 * WireMock tests for {@link LearningDuelAdapter} against a stub of Learning's
 * real internal duel contract:
 * <ul>
 *   <li>{@code POST /duel-sets} body {@code {duelId, languageId}} &rarr;
 *       {@code {setId, exercises[]}} (typed presentations, position order)</li>
 *   <li>{@code GET  /duel-sets/{setId}} &rarr; the same shape</li>
 *   <li>{@code POST /duel-sets/{setId}/answers} body {@code {userId, position,
 *       answer}} &rarr; {@code {position, score, correct}}</li>
 * </ul>
 * Verifies the internal headers, the request/response mapping, and
 * <b>Property 10 (Learning failure safety)</b>: a timeout or repeated 5xx that
 * exhausts the retry budget raises {@link IntegrationException} rather than
 * returning a fabricated {@link ScoreResult}.
 */
class LearningDuelAdapterWireMockTest {

    private static final String API_KEY = "super-secret-internal-key";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final Duration READ_TIMEOUT = Duration.ofMillis(400);

    @RegisterExtension
    static final WireMockExtension WIREMOCK = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private LearningDuelAdapter adapter;

    @BeforeEach
    void setUp() {
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

    /** A Learning duel-set response body: {setId, exercises[]} with typed content. */
    private static String duelSetBody(UUID setId, UUID ex1, UUID ex2) {
        return "{\"setId\":\"" + setId + "\",\"exercises\":["
                + "{\"exerciseId\":\"" + ex1 + "\",\"type\":\"MULTIPLE_CHOICE\",\"prompt\":\"first\","
                + "\"maxPoints\":10,\"difficulty\":\"EASY\",\"requiredForCompletion\":true,\"duelEligible\":true,"
                + "\"content\":{\"multiSelect\":false,\"options\":"
                + "[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}]}},"
                + "{\"exerciseId\":\"" + ex2 + "\",\"type\":\"TRUE_FALSE\",\"prompt\":\"second\","
                + "\"maxPoints\":20,\"difficulty\":\"EASY\",\"requiredForCompletion\":true,\"duelEligible\":true,"
                + "\"content\":{\"statement\":\"s\"}}"
                + "]}";
    }

    @Test
    void createDuelSet_postsRequestAndParsesResponse() {
        UUID duelId = UUID.randomUUID();
        UUID languageId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        UUID ex1 = UUID.randomUUID();
        UUID ex2 = UUID.randomUUID();
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(duelSetBody(setId, ex1, ex2))));

        DuelSetRef ref = adapter.createDuelSet(duelId, languageId);

        assertThat(ref.setId()).isEqualTo(setId);
        assertThat(ref.exercises()).hasSize(2);
        assertThat(ref.exercises().get(0).position()).isZero();
        assertThat(ref.exercises().get(0).type()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(ref.exercises().get(0).options())
                .extracting(ExerciseRef.Option::id).containsExactly("a", "b");

        WIREMOCK.verify(postRequestedFor(urlEqualTo("/internal/v1/duel-sets"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withHeader(CORRELATION_HEADER, matching(".+"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(equalToJson(
                        "{\"duelId\":\"" + duelId + "\",\"languageId\":\"" + languageId + "\"}")));
    }

    @Test
    void fetchExercises_getsRequestAndPreservesOrder() {
        UUID setId = UUID.randomUUID();
        UUID ex1 = UUID.randomUUID();
        UUID ex2 = UUID.randomUUID();
        WIREMOCK.stubFor(get(urlEqualTo("/internal/v1/duel-sets/" + setId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(duelSetBody(setId, ex1, ex2))));

        List<ExerciseRef> exercises = adapter.fetchExercises(setId);

        assertThat(exercises).hasSize(2);
        assertThat(exercises.get(0).position()).isZero();
        assertThat(exercises.get(0).exerciseId()).isEqualTo(ex1);
        assertThat(exercises.get(0).prompt()).isEqualTo("first");
        assertThat(exercises.get(0).options()).extracting(ExerciseRef.Option::id).containsExactly("a", "b");
        assertThat(exercises.get(0).maxPoints()).isEqualTo(10);
        assertThat(exercises.get(1).position()).isEqualTo(1);
        assertThat(exercises.get(1).exerciseId()).isEqualTo(ex2);
        assertThat(exercises.get(1).options()).isEmpty();
        assertThat(exercises.get(1).maxPoints()).isEqualTo(20);

        WIREMOCK.verify(getRequestedFor(urlEqualTo("/internal/v1/duel-sets/" + setId))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withHeader(CORRELATION_HEADER, matching(".+")));
    }

    @Test
    void scoreAnswer_postsAnswerWithUserIdAndPositionAndForwardsIdempotencyKey() {
        UUID setId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID idemKey = UUID.randomUUID();
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"position\":2,\"score\":10,\"correct\":true}")));

        AnswerSubmission submission = new AnswerSubmission(2, Map.of("selectedOptionIds", List.of("a")));
        ScoreResult result = adapter.scoreAnswer(setId, userId, submission, idemKey);

        assertThat(result.correct()).isTrue();
        assertThat(result.awardedPoints()).isEqualTo(10);

        WIREMOCK.verify(postRequestedFor(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withHeader(CORRELATION_HEADER, matching(".+"))
                .withHeader("Idempotency-Key", equalTo(idemKey.toString()))
                .withRequestBody(equalToJson(
                        "{\"userId\":\"" + userId + "\",\"position\":2,"
                                + "\"answer\":{\"selectedOptionIds\":[\"a\"]}}")));
    }

    /** Validates: Requirements 5.1 / Property 10 (transport failure path). */
    @Test
    void scoreAnswer_timeoutExhaustsRetryBudget_raisesIntegrationExceptionAndAdvancesNoState() {
        UUID setId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID idemKey = UUID.randomUUID();
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .willReturn(aResponse().withFault(
                        com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        AnswerSubmission submission = new AnswerSubmission(0, Map.of("answer", true));

        assertThatThrownBy(() -> adapter.scoreAnswer(setId, userId, submission, idemKey))
                .isInstanceOf(IntegrationException.class);

        WIREMOCK.verify(2, postRequestedFor(
                urlPathEqualTo("/internal/v1/duel-sets/" + setId + "/answers")));
    }

    /** Validates: Requirements 5.1 / Property 10 (repeated 5xx path). */
    @Test
    void scoreAnswer_repeated5xxExhaustsRetryBudget_raisesIntegrationExceptionAndAdvancesNoState() {
        UUID setId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID idemKey = UUID.randomUUID();
        WIREMOCK.stubFor(post(urlEqualTo("/internal/v1/duel-sets/" + setId + "/answers"))
                .willReturn(aResponse().withStatus(503)));

        AnswerSubmission submission = new AnswerSubmission(0, Map.of("answer", true));

        assertThatThrownBy(() -> adapter.scoreAnswer(setId, userId, submission, idemKey))
                .isInstanceOf(IntegrationException.class);

        WIREMOCK.verify(2, postRequestedFor(
                urlPathEqualTo("/internal/v1/duel-sets/" + setId + "/answers")));
    }

    @Test
    void scoreAnswer_5xxThenSuccess_retriesOnceAndReturnsScore() {
        UUID setId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
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
                        .withBody("{\"position\":0,\"score\":0,\"correct\":false}")));

        ScoreResult result = adapter.scoreAnswer(
                setId, userId, new AnswerSubmission(0, Map.of("answer", true)), idemKey);

        assertThat(result.correct()).isFalse();
        assertThat(result.awardedPoints()).isZero();
        WIREMOCK.verify(2, postRequestedFor(
                urlPathEqualTo("/internal/v1/duel-sets/" + setId + "/answers")));
    }
}
