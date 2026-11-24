package hu.bme.szoftarch.learning.support;

import java.util.Map;

import hu.bme.szoftarch.learning.api.NotFoundException;
import hu.bme.szoftarch.learning.infrastructure.security.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teszt-only controller a security és a globális hibakezelő vizsgálatához.
 * Csak a teszt classpathon létezik; éles kódba nem kerül.
 */
@RestController
public class TestApiController {

    private final CurrentUser currentUser;

    public TestApiController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/test/echo")
    public Map<String, String> echo() {
        return Map.of("userId", currentUser.requireUserId().toString());
    }

    @GetMapping("/api/v1/test/boom")
    public Map<String, String> boom() {
        throw new NotFoundException("resource_not_found", "The requested resource was not found");
    }
}
