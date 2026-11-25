package hu.bme.szoftarch.learning.infrastructure.client;

import java.util.UUID;

import hu.bme.szoftarch.learning.domain.port.AiEvaluationException;
import hu.bme.szoftarch.learning.domain.port.AiEvaluationPort;
import hu.bme.szoftarch.learning.domain.port.CompleteScenarioCommand;
import hu.bme.szoftarch.learning.domain.port.EvaluateTranslationCommand;
import hu.bme.szoftarch.learning.domain.port.ScenarioEvaluation;
import hu.bme.szoftarch.learning.domain.port.ScenarioMessage;
import hu.bme.szoftarch.learning.domain.port.ScenarioSession;
import hu.bme.szoftarch.learning.domain.port.SendMessageCommand;
import hu.bme.szoftarch.learning.domain.port.StartScenarioCommand;
import hu.bme.szoftarch.learning.domain.port.TranslationEvaluation;
import hu.bme.szoftarch.learning.infrastructure.client.dto.ScenarioCompleteResponse;
import hu.bme.szoftarch.learning.infrastructure.client.dto.ScenarioMessageRequest;
import hu.bme.szoftarch.learning.infrastructure.client.dto.ScenarioMessageResponse;
import hu.bme.szoftarch.learning.infrastructure.client.dto.ScenarioSessionRequest;
import hu.bme.szoftarch.learning.infrastructure.client.dto.ScenarioSessionResponse;
import hu.bme.szoftarch.learning.infrastructure.client.dto.TranslationEvaluateRequest;
import hu.bme.szoftarch.learning.infrastructure.client.dto.TranslationEvaluateResponse;
import hu.bme.szoftarch.learning.infrastructure.security.InternalApiKeyFilter;
import hu.bme.szoftarch.learning.infrastructure.security.InternalApiProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Az {@link AiEvaluationPort} RestClient adaptere (TERV B – U8, Adapter minta).
 * A négy belső AI-végpontot (TERV 7.7) hívja a {@code aiRestClient} beanen
 * (connect 2s / read 20s, U0), {@code X-Internal-Api-Key} headerrel (TERV 8.):
 *
 * <ul>
 *   <li>{@code POST {AI_URL}/internal/v1/translations:evaluate}</li>
 *   <li>{@code POST {AI_URL}/internal/v1/scenario-sessions}</li>
 *   <li>{@code POST {AI_URL}/internal/v1/scenario-sessions/{id}/messages}</li>
 *   <li>{@code POST {AI_URL}/internal/v1/scenario-sessions/{id}/complete}</li>
 * </ul>
 *
 * <p>Hibakezelés: 5xx, connect/read timeout vagy hálózati hiba
 * ({@code ResourceAccessException} stb.), illetve hiányzó/hibás válasz-body
 * egységesen {@link AiEvaluationException}.</p>
 *
 * <p><strong>Biztonság:</strong> a belső API-kulcs, a token és a body SOHA nem
 * kerül logba; csak a kimenet és a HTTP-státusz naplózódik.</p>
 */
@Component
public class AiClient implements AiEvaluationPort {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final RestClient restClient;
    private final InternalApiProperties internalApiProperties;
    private final AiClientProperties aiProperties;

    public AiClient(RestClient aiRestClient, InternalApiProperties internalApiProperties,
            AiClientProperties aiProperties) {
        this.restClient = aiRestClient;
        this.internalApiProperties = internalApiProperties;
        this.aiProperties = aiProperties;
    }

    @Override
    public TranslationEvaluation evaluateTranslation(EvaluateTranslationCommand command) {
        TranslationEvaluateRequest body = new TranslationEvaluateRequest(
                command.sourceText(), command.targetLanguageCode(), command.rubric(),
                command.userTranslation());
        String uri = aiProperties.url() + "/internal/v1/translations:evaluate";
        TranslationEvaluateResponse response = post(uri, body, TranslationEvaluateResponse.class,
                "translation evaluate");
        if (response.score() == null) {
            throw malformed("translation evaluate");
        }
        return new TranslationEvaluation(response.score(), response.accepted(), response.feedback());
    }

    @Override
    public ScenarioSession startScenarioSession(StartScenarioCommand command) {
        ScenarioSessionRequest body = new ScenarioSessionRequest(
                command.role(), command.situation(), command.goals(),
                command.targetLanguageCode(), command.openingMessage());
        String uri = aiProperties.url() + "/internal/v1/scenario-sessions";
        ScenarioSessionResponse response = post(uri, body, ScenarioSessionResponse.class,
                "scenario start");
        if (response.id() == null) {
            throw malformed("scenario start");
        }
        return new ScenarioSession(response.id(), response.openingMessage());
    }

    @Override
    public ScenarioMessage sendScenarioMessage(SendMessageCommand command) {
        ScenarioMessageRequest body = new ScenarioMessageRequest(command.userMessage());
        String uri = aiProperties.url() + "/internal/v1/scenario-sessions/"
                + command.aiSessionId() + "/messages";
        ScenarioMessageResponse response = post(uri, body, ScenarioMessageResponse.class,
                "scenario message");
        if (response.message() == null) {
            throw malformed("scenario message");
        }
        return new ScenarioMessage(response.message(), response.turnCount());
    }

    @Override
    public ScenarioEvaluation completeScenarioSession(CompleteScenarioCommand command) {
        UUID sessionId = command.aiSessionId();
        String uri = aiProperties.url() + "/internal/v1/scenario-sessions/" + sessionId + "/complete";
        ScenarioCompleteResponse response = post(uri, null, ScenarioCompleteResponse.class,
                "scenario complete");
        if (response.comprehensibility() == null || response.grammar() == null
                || response.taskCompletion() == null) {
            throw malformed("scenario complete");
        }
        return new ScenarioEvaluation(response.comprehensibility(), response.grammar(),
                response.taskCompletion(), response.feedback());
    }

    private <T> T post(String uri, Object body, Class<T> responseType, String operation) {
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(InternalApiKeyFilter.HEADER, internalApiProperties.apiKey());
            if (body != null) {
                request.body(body);
            }
            T response = request.retrieve().body(responseType);
            if (response == null) {
                throw malformed(operation);
            }
            log.info("AI call succeeded: operation={} status=OK", operation);
            return response;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("AI call failed: operation={} status={}", operation, status);
            throw new AiEvaluationException("AI " + operation + " failed with status " + status, e);
        } catch (AiEvaluationException e) {
            throw e;
        } catch (Exception e) {
            // Connect/read timeout vagy hálózati hiba (ResourceAccessException stb.).
            log.warn("AI call failed: operation={} error={}", operation, e.getClass().getSimpleName());
            throw new AiEvaluationException("AI " + operation + " failed without response", e);
        }
    }

    private static AiEvaluationException malformed(String operation) {
        log.warn("AI call returned malformed body: operation={}", operation);
        return new AiEvaluationException("AI " + operation + " returned a missing or malformed body");
    }
}
