package hu.bme.szoftarch.learning;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;

import hu.bme.szoftarch.learning.domain.port.AiEvaluationException;
import hu.bme.szoftarch.learning.domain.port.CompleteScenarioCommand;
import hu.bme.szoftarch.learning.domain.port.EvaluateTranslationCommand;
import hu.bme.szoftarch.learning.domain.port.ScenarioEvaluation;
import hu.bme.szoftarch.learning.domain.port.ScenarioMessage;
import hu.bme.szoftarch.learning.domain.port.ScenarioSession;
import hu.bme.szoftarch.learning.domain.port.SendMessageCommand;
import hu.bme.szoftarch.learning.domain.port.StartScenarioCommand;
import hu.bme.szoftarch.learning.domain.port.TranslationEvaluation;
import hu.bme.szoftarch.learning.infrastructure.client.AiClient;
import hu.bme.szoftarch.learning.infrastructure.client.AiClientProperties;
import hu.bme.szoftarch.learning.infrastructure.security.InternalApiProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * WireMock teszt az {@link AiClient} adapterre (TERV B – U8). A valódi kimenő
 * HTTP-hívást egy helyi WireMock szerver fogadja; így bizonyítjuk a négy belső
 * AI-végpont hívását (TERV 7.7), a {@code X-Internal-Api-Key} headert, a body
 * tartalmát (csak feladat-konfiguráció + felhasználói szöveg), és a hibakezelést
 * (5xx / hálózati hiba / hibás body → {@link AiEvaluationException}). Az adapter
 * a Spring által konfigurált {@code aiRestClient} beanre épül (U0, connect 2s /
 * read 20s, Jackson 3), ezt injektáljuk.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiClientWireMockTest {

    private static final String API_KEY = "test-internal-api-key-at-least-32-bytes-value";
    private static final String HEADER = "X-Internal-Api-Key";

    private final RestClient aiRestClient;

    @Autowired
    AiClientWireMockTest(RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    private WireMockServer wireMock;
    private AiClient client;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        client = new AiClient(aiRestClient,
                new InternalApiProperties(API_KEY),
                new AiClientProperties("http://localhost:" + wireMock.port()));
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    void evaluateTranslationSendsConfigAndMapsResponse() {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/translations:evaluate"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"score\":0.82,\"accepted\":true,\"feedback\":\"jó\"}")));

        TranslationEvaluation result = client.evaluateTranslation(new EvaluateTranslationCommand(
                "Jó reggelt", "en", List.of("pontosság", "nyelvtan"), "Good morning"));

        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("0.82"));
        assertThat(result.accepted()).isTrue();
        assertThat(result.feedback()).isEqualTo("jó");
        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/translations:evaluate"))
                .withHeader(HEADER, equalTo(API_KEY))
                .withRequestBody(matchingJsonPath("$.sourceText", equalTo("Jó reggelt")))
                .withRequestBody(matchingJsonPath("$.targetLanguage", equalTo("en")))
                .withRequestBody(matchingJsonPath("$.learnerTranslation", equalTo("Good morning")))
                .withRequestBody(matchingJsonPath("$.email", absent()))
                .withRequestBody(matchingJsonPath("$.token", absent())));
    }

    @Test
    void startScenarioSessionSendsConfigAndMapsResponse() {
        UUID sessionId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/scenario-sessions"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + sessionId + "\",\"openingMessage\":\"Hello!\"}")));

        ScenarioSession result = client.startScenarioSession(new StartScenarioCommand(
                "pincér", "étterem", List.of("rendelj"), "en", "Welcome!"));

        assertThat(result.aiSessionId()).isEqualTo(sessionId);
        assertThat(result.openingMessage()).isEqualTo("Hello!");
        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/scenario-sessions"))
                .withHeader(HEADER, equalTo(API_KEY))
                .withRequestBody(matchingJsonPath("$.role", equalTo("pincér")))
                .withRequestBody(matchingJsonPath("$.situation", equalTo("étterem")))
                .withRequestBody(matchingJsonPath("$.targetLanguageCode", equalTo("en")))
                .withRequestBody(matchingJsonPath("$.email", absent()))
                .withRequestBody(matchingJsonPath("$.token", absent())));
    }

    @Test
    void sendScenarioMessageSendsUserTextAndMapsResponse() {
        UUID sessionId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/scenario-sessions/" + sessionId + "/messages"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"aiMessage\":\"Sure.\",\"turnCount\":2}")));

        ScenarioMessage result = client.sendScenarioMessage(new SendMessageCommand(
                sessionId, "I would like a coffee"));

        assertThat(result.aiMessage()).isEqualTo("Sure.");
        assertThat(result.turnCount()).isEqualTo(2);
        wireMock.verify(postRequestedFor(
                urlEqualTo("/internal/v1/scenario-sessions/" + sessionId + "/messages"))
                .withHeader(HEADER, equalTo(API_KEY))
                .withRequestBody(matchingJsonPath("$.message", equalTo("I would like a coffee")))
                .withRequestBody(matchingJsonPath("$.email", absent()))
                .withRequestBody(matchingJsonPath("$.token", absent())));
    }

    @Test
    void completeScenarioSessionMapsResponse() {
        UUID sessionId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/scenario-sessions/" + sessionId + "/complete"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"comprehensibility\":0.8,\"grammar\":0.7,"
                                + "\"taskCompletion\":0.9,\"feedback\":\"szép\"}")));

        ScenarioEvaluation result = client.completeScenarioSession(
                new CompleteScenarioCommand(sessionId));

        assertThat(result.comprehensibility()).isEqualByComparingTo(new BigDecimal("0.8"));
        assertThat(result.grammar()).isEqualByComparingTo(new BigDecimal("0.7"));
        assertThat(result.taskCompletion()).isEqualByComparingTo(new BigDecimal("0.9"));
        wireMock.verify(postRequestedFor(
                urlEqualTo("/internal/v1/scenario-sessions/" + sessionId + "/complete"))
                .withHeader(HEADER, equalTo(API_KEY)));
    }

    @Test
    void serverErrorMapsToAiEvaluationException() {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/translations:evaluate"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.evaluateTranslation(new EvaluateTranslationCommand(
                "Jó reggelt", "en", List.of("pontosság"), "Good morning")))
                .isInstanceOf(AiEvaluationException.class);
    }

    @Test
    void networkFaultMapsToAiEvaluationException() {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/translations:evaluate"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.evaluateTranslation(new EvaluateTranslationCommand(
                "Jó reggelt", "en", List.of("pontosság"), "Good morning")))
                .isInstanceOf(AiEvaluationException.class);
    }

    @Test
    void malformedBodyMapsToAiEvaluationException() {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/translations:evaluate"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        assertThatThrownBy(() -> client.evaluateTranslation(new EvaluateTranslationCommand(
                "Jó reggelt", "en", List.of("pontosság"), "Good morning")))
                .isInstanceOf(AiEvaluationException.class);
    }
}
