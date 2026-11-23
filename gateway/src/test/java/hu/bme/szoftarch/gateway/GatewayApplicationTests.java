package hu.bme.szoftarch.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("Context loads successfully")
    void contextLoads() {
    }

    @Test
    @DisplayName("Gateway generates X-Correlation-Id if not provided in incoming request")
    void correlationId_generatedWhenMissing() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId -> {
                    assertThat(correlationId).isNotBlank();
                });
    }

    @Test
    @DisplayName("Gateway preserves and propagates existing X-Correlation-Id")
    void correlationId_preservedWhenProvided() {
        String customCorrelationId = "test-custom-trace-uuid-999";

        webTestClient.get()
                .uri("/actuator/health")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, customCorrelationId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationIdFilter.CORRELATION_ID_HEADER, customCorrelationId);
    }

    @Test
    @DisplayName("Internal paths (/internal/**) are not routed and return 404")
    void internalEndpoints_notRouted_returns404() {
        webTestClient.get()
                .uri("/internal/v1/users/points")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().value(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId -> {
                    assertThat(correlationId).isNotBlank();
                });
    }
}
