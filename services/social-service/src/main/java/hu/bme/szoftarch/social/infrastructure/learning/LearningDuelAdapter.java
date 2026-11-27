package hu.bme.szoftarch.social.infrastructure.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import hu.bme.szoftarch.social.domain.duel.ScoreResult;
import hu.bme.szoftarch.social.domain.port.AnswerSubmission;
import hu.bme.szoftarch.social.domain.port.DuelSetRef;
import hu.bme.szoftarch.social.domain.port.ExerciseRef;
import hu.bme.szoftarch.social.domain.port.LearningDuelPort;

/**
 * Adapter-pattern implementation of {@link LearningDuelPort} backed by the
 * Learning service internal API.
 *
 * <p>Endpoints (design "Outbound ports"):
 * <ul>
 *   <li>{@code POST /internal/v1/duel-sets} &rarr; {@link #createDuelSet}</li>
 *   <li>{@code GET  /internal/v1/duel-sets/{setId}} &rarr; {@link #fetchExercises}</li>
 *   <li>{@code POST /internal/v1/duel-sets/{setId}/answers} &rarr; {@link #scoreAnswer}
 *       (forwards the caller's {@code Idempotency-Key})</li>
 * </ul>
 *
 * <p>Reliability (design "Outbound adapter reliability", requirements
 * 12.1&ndash;12.3): every call carries {@code X-Internal-Api-Key} (a constant
 * default header on the {@link RestClient}) and a per-call {@code X-Correlation-Id};
 * the underlying factory enforces a 2s connect / 5s read timeout. Each of the
 * three operations is retried at most once on a timeout or a 5xx response,
 * because all three are either idempotent reads/creates or carry an
 * Idempotency-Key. After the retry budget is exhausted the adapter raises
 * {@link IntegrationException} and performs no state mutation and no compensating
 * write (requirements 5.1, 5.2, 12.4).
 *
 * <p>The internal API key, tokens, and correct answers are never logged
 * (requirement 13.2).
 */
@Component
public class LearningDuelAdapter implements LearningDuelPort {

    private static final Logger log = LoggerFactory.getLogger(LearningDuelAdapter.class);

    /** At most one retry on timeout/5xx => two attempts total (requirement 12.3). */
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final LearningClientProperties properties;
    private final CorrelationIdAccessor correlationId;

    public LearningDuelAdapter(@Qualifier(LearningHttpClient.REST_CLIENT_BEAN) RestClient restClient,
                               LearningClientProperties properties,
                               CorrelationIdAccessor correlationId) {
        this.restClient = restClient;
        this.properties = properties;
        this.correlationId = correlationId;
    }

    @Override
    public DuelSetRef createDuelSet(UUID languageId, int exerciseCount) {
        if (languageId == null) {
            throw new IllegalArgumentException("languageId must not be null");
        }
        CreateDuelSetRequest body = new CreateDuelSetRequest(languageId, exerciseCount);
        DuelSetResponse response = callWithRetry("POST /internal/v1/duel-sets", () ->
                restClient.post()
                        .uri("/duel-sets")
                        .header(properties.correlationHeader(), correlationId.current())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(DuelSetResponse.class));
        if (response == null) {
            throw new IntegrationException("Learning returned an empty duel-set response");
        }
        return new DuelSetRef(response.setId(), response.exerciseCount());
    }

    @Override
    public List<ExerciseRef> fetchExercises(UUID setId) {
        if (setId == null) {
            throw new IllegalArgumentException("setId must not be null");
        }
        ExerciseResponse[] response = callWithRetry("GET /internal/v1/duel-sets/{setId}", () ->
                restClient.get()
                        .uri("/duel-sets/{setId}", setId)
                        .header(properties.correlationHeader(), correlationId.current())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(ExerciseResponse[].class));
        if (response == null) {
            return List.of();
        }
        List<ExerciseRef> exercises = new ArrayList<>(response.length);
        for (ExerciseResponse e : response) {
            exercises.add(new ExerciseRef(e.exerciseId(), e.prompt(),
                    e.options() == null ? List.of() : e.options(), e.maxPoints()));
        }
        return List.copyOf(exercises);
    }

    @Override
    public ScoreResult scoreAnswer(UUID setId, AnswerSubmission submission, UUID idemKey) {
        if (setId == null) {
            throw new IllegalArgumentException("setId must not be null");
        }
        if (submission == null) {
            throw new IllegalArgumentException("submission must not be null");
        }
        if (idemKey == null) {
            throw new IllegalArgumentException("idemKey must not be null");
        }
        ScoreAnswerRequest body = new ScoreAnswerRequest(submission.exerciseId(), submission.answer());
        ScoreAnswerResponse response = callWithRetry("POST /internal/v1/duel-sets/{setId}/answers", () ->
                restClient.post()
                        .uri("/duel-sets/{setId}/answers", setId)
                        .header(properties.correlationHeader(), correlationId.current())
                        // Forward the caller's Idempotency-Key so Learning scores at-most-once.
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(ScoreAnswerResponse.class));
        if (response == null) {
            throw new IntegrationException("Learning returned an empty score response");
        }
        return new ScoreResult(response.correct(), response.awardedPoints());
    }

    /**
     * Executes {@code call}, retrying at most once on a timeout or 5xx response.
     *
     * <p>Failure classification covers the whole {@link RestClientException}
     * hierarchy so the production stack ({@code DefaultRestClient} +
     * {@code SimpleClientHttpRequestFactory}) can never leak a raw
     * {@code RestClientException}:
     * <ul>
     *   <li>A {@link RestClientResponseException} with a 4xx status is a
     *       deterministic client error: it is <b>not</b> retried and is surfaced
     *       immediately as an {@link IntegrationException} (fast-fail 4xx).</li>
     *   <li>Everything else &mdash; a 5xx response, a {@code ResourceAccessException}
     *       connect/read timeout, or a bare read-phase {@code RestClientException}
     *       raised while the response is being read &mdash; is a retryable
     *       transport error: retried at most once, then wrapped in
     *       {@link IntegrationException} once the budget is exhausted.</li>
     * </ul>
     * Either way the application layer never advances state on a failed Learning
     * call, and a raw {@code RestClientException} never escapes for the
     * timeout/transport case (requirements 5.1, 12.4; Property 10).
     */
    private <T> T callWithRetry(String operation, Supplier<T> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RestClientResponseException http) {
                if (http.getStatusCode().is5xxServerError()) {
                    // 5xx server error: retryable.
                    last = http;
                    logRetryable(operation, attempt, http.getStatusCode().toString());
                } else {
                    // 4xx and other non-5xx status responses: not retryable, fail immediately.
                    throw new IntegrationException(
                            "Learning call " + operation + " failed with " + http.getStatusCode(), http);
                }
            } catch (RestClientException transport) {
                // Connect/read timeout, connection reset, or a bare read-phase
                // RestClientException where no usable response was produced: retryable.
                last = transport;
                logRetryable(operation, attempt, "timeout/transport error");
            }
        }
        throw new IntegrationException(
                "Learning call " + operation + " failed after " + MAX_ATTEMPTS + " attempts", last);
    }

    private void logRetryable(String operation, int attempt, String reason) {
        if (attempt < MAX_ATTEMPTS) {
            log.warn("Learning call {} attempt {} failed ({}); retrying", operation, attempt, reason);
        } else {
            log.warn("Learning call {} attempt {} failed ({}); retry budget exhausted", operation, attempt, reason);
        }
    }

    // --- Wire DTOs (local to the adapter; never leak into the domain). ---

    private record CreateDuelSetRequest(UUID languageId, int exerciseCount) {
    }

    private record DuelSetResponse(UUID setId, int exerciseCount) {
    }

    private record ExerciseResponse(UUID exerciseId, String prompt, List<String> options, int maxPoints) {
    }

    private record ScoreAnswerRequest(UUID exerciseId, String answer) {
    }

    private record ScoreAnswerResponse(boolean correct, int awardedPoints) {
    }
}
