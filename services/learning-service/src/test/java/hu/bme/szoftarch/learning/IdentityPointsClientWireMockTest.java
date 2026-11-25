package hu.bme.szoftarch.learning;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;

import hu.bme.szoftarch.learning.domain.port.AwardPointsCommand;
import hu.bme.szoftarch.learning.domain.port.AwardPointsResult;
import hu.bme.szoftarch.learning.infrastructure.client.IdentityClientProperties;
import hu.bme.szoftarch.learning.infrastructure.client.IdentityPointsClient;
import hu.bme.szoftarch.learning.infrastructure.security.InternalApiProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * WireMock teszt az {@link IdentityPointsClient} adapterre (TERV B – U5). A
 * valódi kimenő HTTP-hívást egy helyi WireMock szerver fogadja; így bizonyítjuk
 * a kimenet-leképezést (2xx/4xx/5xx) és a {@code X-Internal-Api-Key} header +
 * body tartalmát. Az adapter a Spring által konfigurált {@code defaultRestClient}
 * beanre épül (U0, connect 2s / read 5s, Jackson 3 konverterek), ezt injektáljuk.
 */
@SpringBootTest
@ActiveProfiles("test")
class IdentityPointsClientWireMockTest {

    private static final String API_KEY = "test-internal-api-key-at-least-32-bytes-value";

    private final RestClient defaultRestClient;

    @Autowired
    IdentityPointsClientWireMockTest(RestClient defaultRestClient) {
        this.defaultRestClient = defaultRestClient;
    }

    private WireMockServer wireMock;
    private IdentityPointsClient client;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        client = new IdentityPointsClient(defaultRestClient,
                new InternalApiProperties(API_KEY),
                new IdentityClientProperties("http://localhost:" + wireMock.port()));
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    private AwardPointsCommand command(UUID userId, UUID exerciseId) {
        return new AwardPointsCommand(userId, "EXERCISE", exerciseId, 15,
                "exercise:" + userId + ":" + exerciseId + ":first-success");
    }

    @Test
    void successMapsToAwarded() {
        UUID userId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/users/" + userId + "/points"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AWARDED\"}")));

        AwardPointsResult result = client.award(command(userId, exerciseId));

        assertThat(result).isEqualTo(AwardPointsResult.AWARDED);
    }

    @Test
    void serverErrorMapsToRetryable() {
        UUID userId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/users/" + userId + "/points"))
                .willReturn(aResponse().withStatus(503)));

        AwardPointsResult result = client.award(command(userId, exerciseId));

        assertThat(result).isEqualTo(AwardPointsResult.RETRYABLE_ERROR);
    }

    @Test
    void clientErrorMapsToNonRetryable() {
        UUID userId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/users/" + userId + "/points"))
                .willReturn(aResponse().withStatus(422)));

        AwardPointsResult result = client.award(command(userId, exerciseId));

        assertThat(result).isEqualTo(AwardPointsResult.NON_RETRYABLE_ERROR);
    }

    @Test
    void outgoingRequestCarriesInternalApiKeyHeaderAndBody() {
        UUID userId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        String idempotencyKey = "exercise:" + userId + ":" + exerciseId + ":first-success";
        wireMock.stubFor(post(urlEqualTo("/internal/v1/users/" + userId + "/points"))
                .willReturn(aResponse().withStatus(200)));

        client.award(new AwardPointsCommand(userId, "EXERCISE", exerciseId, 15, idempotencyKey));

        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/users/" + userId + "/points"))
                .withHeader("X-Internal-Api-Key", equalTo(API_KEY))
                .withRequestBody(equalToJson("{"
                        + "\"sourceType\":\"EXERCISE\","
                        + "\"sourceId\":\"" + exerciseId + "\","
                        + "\"points\":15,"
                        + "\"idempotencyKey\":\"" + idempotencyKey + "\"}"))
                .withRequestBody(matchingJsonPath("$.idempotencyKey", equalTo(idempotencyKey))));
    }
}
