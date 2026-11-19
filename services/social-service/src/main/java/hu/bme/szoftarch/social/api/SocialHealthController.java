package hu.bme.szoftarch.social.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Skeleton health végpont. A service gazdája ezt a csomagot tölti fel
 * a valódi REST controllerekkel (D05: api réteg).
 */
@RestController
public class SocialHealthController {

    @GetMapping("/api/v1/social/health")
    public Map<String, String> health() {
        return Map.of("service", "social-service", "status", "UP");
    }
}
