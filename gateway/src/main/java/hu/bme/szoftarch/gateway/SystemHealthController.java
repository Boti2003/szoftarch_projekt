package hu.bme.szoftarch.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregált rendszer-health a frontend health dashboardhoz (10.7).
 * Végigkérdezi a domain service-ek actuator health végpontját, és
 * egyetlen válaszban adja vissza a státuszukat.
 */
@RestController
public class SystemHealthController {

    private final WebClient webClient = WebClient.builder().build();

    private final Map<String, String> downstreams = new LinkedHashMap<>();

    public SystemHealthController(
            @Value("${gateway.downstream.identity}") String identity,
            @Value("${gateway.downstream.learning}") String learning,
            @Value("${gateway.downstream.social}") String social,
            @Value("${gateway.downstream.ai}") String ai) {
        downstreams.put("identity", identity);
        downstreams.put("learning", learning);
        downstreams.put("social", social);
        downstreams.put("ai", ai);
    }

    @GetMapping(value = "/api/v1/system/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> systemHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gateway", "UP");

        return Mono.zip(
                downstreams.entrySet().stream()
                        .map(e -> probe(e.getKey(), e.getValue()))
                        .toList(),
                values -> {
                    boolean allUp = true;
                    for (Object v : values) {
                        @SuppressWarnings("unchecked")
                        Map.Entry<String, String> entry = (Map.Entry<String, String>) v;
                        result.put(entry.getKey(), entry.getValue());
                        if (!"UP".equals(entry.getValue())) {
                            allUp = false;
                        }
                    }
                    result.put("status", allUp ? "UP" : "DEGRADED");
                    return result;
                });
    }

    private Mono<Map.Entry<String, String>> probe(String name, String baseUrl) {
        return webClient.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> Map.entry(name, String.valueOf(body.getOrDefault("status", "UNKNOWN"))))
                .timeout(Duration.ofSeconds(3))
                .onErrorReturn(Map.entry(name, "DOWN"));
    }
}
