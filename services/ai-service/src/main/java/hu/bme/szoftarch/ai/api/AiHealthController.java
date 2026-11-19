package hu.bme.szoftarch.ai.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Skeleton health végpont. A service gazdája ezt a csomagot tölti fel
 * a valódi REST controllerekkel (D05: api réteg).
 */
@RestController
public class AiHealthController {

    @GetMapping("/api/v1/ai/health")
    public Map<String, String> health() {
        return Map.of("service", "ai-service", "status", "UP");
    }
}
