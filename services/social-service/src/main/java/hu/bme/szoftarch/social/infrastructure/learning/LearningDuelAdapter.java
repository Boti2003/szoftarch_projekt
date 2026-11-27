package hu.bme.szoftarch.social.infrastructure.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import hu.bme.szoftarch.social.domain.duel.ScoreResult;
import hu.bme.szoftarch.social.domain.port.AnswerSubmission;
import hu.bme.szoftarch.social.domain.port.DuelSetRef;
import hu.bme.szoftarch.social.domain.port.ExerciseRef;
import hu.bme.szoftarch.social.domain.port.LearningDuelPort;

/**
 * Adapter-pattern implementation of {@link LearningDuelPort} backed by the
 * Learning service internal API ({@code /internal/v1/duel-sets}).
 *
 * <p>Learning's real contract:
 * <ul>
 *   <li>{@code POST /duel-sets} body {@code {duelId, languageId}} &rarr;
 *       {@code {setId, exercises[]}} (typed presentations, position order).</li>
 *   <li>{@code GET  /duel-sets/{setId}} &rarr; the same shape.</li>
 *   <li>{@code POST /duel-sets/{setId}/answers} body {@code {userId, position,
 *       answer}} &rarr; {@code {position, score, correct}}. Learning is
 *       idempotent on {@code (setId, userId, position)}.</li>
 * </ul>
 *
 * <p>Reliability: every call carries {@code X-Internal-Api-Key} (a constant
 * default header) and a per-call {@code X-Correlation-Id}; the factory enforces
 * a 2s connect / 5s read timeout. Each call is retried at most once on a timeout
 * or 5xx; a 4xx fails fast. After the budget is exhausted the adapter raises
 * {@link IntegrationException} and performs no state mutation, so the caller
 * leaves participant state unchanged (design "Learning failure safety").
 */
@Component
public class LearningDuelAdapter implements LearningDuelPort {

    private static final Logger log = LoggerFactory.getLogger(LearningDuelAdapter.class);

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
    public DuelSetRef createDuelSet(UUID duelId, UUID languageId) {
        if (duelId == null) {
            throw new IllegalArgumentException("duelId must not be null");
        }
        if (languageId == null) {
            throw new IllegalArgumentException("languageId must not be null");
        }
        CreateDuelSetRequest body = new CreateDuelSetRequest(duelId, languageId);
        DuelSetResponse response = callWithRetry("POST /internal/v1/duel-sets", () ->
                restClient.post()
                        .uri("/duel-sets")
                        .header(properties.correlationHeader(), correlationId.current())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(DuelSetResponse.class));
        if (response == null || response.setId() == null) {
            throw new IntegrationException("Learning returned an empty duel-set response");
        }
        return new DuelSetRef(response.setId(), toExerciseRefs(response.exercises()));
    }

    @Override
    public List<ExerciseRef> fetchExercises(UUID setId) {
        if (setId == null) {
            throw new IllegalArgumentException("setId must not be null");
        }
        DuelSetResponse response = callWithRetry("GET /internal/v1/duel-sets/{setId}", () ->
                restClient.get()
                        .uri("/duel-sets/{setId}", setId)
                        .header(properties.correlationHeader(), correlationId.current())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(DuelSetResponse.class));
        if (response == null) {
            return List.of();
        }
        return toExerciseRefs(response.exercises());
    }

    @Override
    public ScoreResult scoreAnswer(UUID setId, UUID userId, AnswerSubmission submission, UUID idemKey) {
        if (setId == null) {
            throw new IllegalArgumentException("setId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (submission == null) {
            throw new IllegalArgumentException("submission must not be null");
        }
        if (idemKey == null) {
            throw new IllegalArgumentException("idemKey must not be null");
        }
        DuelAnswerRequest body = new DuelAnswerRequest(userId, submission.position(), submission.answer());
        DuelAnswerResponse response = callWithRetry("POST /internal/v1/duel-sets/{setId}/answers", () ->
                restClient.post()
                        .uri("/duel-sets/{setId}/answers", setId)
                        .header(properties.correlationHeader(), correlationId.current())
                        // Forwarded for traceability; Learning is idempotent on (setId,userId,position).
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(DuelAnswerResponse.class));
        if (response == null) {
            throw new IntegrationException("Learning returned an empty score response");
        }
        return new ScoreResult(response.correct(), response.score());
    }

    // --- mapping ----------------------------------------------------------

    /** Maps Learning's typed presentations to position-ordered {@link ExerciseRef}s. */
    private static List<ExerciseRef> toExerciseRefs(List<ExercisePresentationResponse> exercises) {
        if (exercises == null) {
            return List.of();
        }
        List<ExerciseRef> refs = new ArrayList<>(exercises.size());
        for (int position = 0; position < exercises.size(); position++) {
            ExercisePresentationResponse e = exercises.get(position);
            Map<String, Object> content = e.content() == null ? Map.of() : e.content();
            refs.add(new ExerciseRef(
                    position,
                    e.exerciseId(),
                    e.type(),
                    e.prompt(),
                    optionsOf(content),
                    content,
                    e.maxPoints()));
        }
        return List.copyOf(refs);
    }

    /**
     * Extracts selectable options from the raw content for the option-based duel
     * types: multiple choice exposes {@code options}, odd-one-out exposes
     * {@code items}; both are lists of {@code {id, text}}. Other types yield no
     * options (the UI renders them from the full {@code content}).
     */
    @SuppressWarnings("unchecked")
    private static List<ExerciseRef.Option> optionsOf(Map<String, Object> content) {
        Object raw = content.get("options");
        if (raw == null) {
            raw = content.get("items");
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ExerciseRef.Option> options = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Object id = m.get("id");
                Object text = m.get("text");
                options.add(new ExerciseRef.Option(
                        id == null ? null : id.toString(),
                        text == null ? null : text.toString()));
            }
        }
        return options;
    }

    private <T> T callWithRetry(String operation, Supplier<T> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RestClientResponseException http) {
                if (http.getStatusCode().is5xxServerError()) {
                    last = http;
                    logRetryable(operation, attempt, http.getStatusCode().toString());
                } else {
                    // 4xx: a client error (e.g. malformed/type-mismatched answer).
                    // Not retryable; surfaced distinctly so the caller maps it to 400.
                    throw new IntegrationClientException(
                            "Learning call " + operation + " rejected with " + http.getStatusCode(), http);
                }
            } catch (RestClientException transport) {
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

    private record CreateDuelSetRequest(UUID duelId, UUID languageId) {
    }

    /** Learning's {@code POST/GET duel-sets} response: {@code {setId, exercises[]}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DuelSetResponse(UUID setId, List<ExercisePresentationResponse> exercises) {
    }

    /**
     * A subset of Learning's {@code ExercisePresentationResponse} — only the
     * fields the duel UI needs. Unknown fields (difficulty, flags, type-specific
     * content shapes) are ignored. {@code content.options} is read where present
     * (multiple choice / odd-one-out); other content shapes contribute no options.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExercisePresentationResponse(
            UUID exerciseId,
            String type,
            String prompt,
            int maxPoints,
            Map<String, Object> content) {
    }

    /** Learning's answer request: {@code {userId, position, answer}}. */
    private record DuelAnswerRequest(UUID userId, int position, Map<String, Object> answer) {
    }

    /** Learning's answer response: {@code {position, score, correct}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DuelAnswerResponse(int position, int score, boolean correct) {
    }
}
