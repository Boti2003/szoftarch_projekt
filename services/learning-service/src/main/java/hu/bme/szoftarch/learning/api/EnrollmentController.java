package hu.bme.szoftarch.learning.api;

import java.util.UUID;

import hu.bme.szoftarch.learning.api.dto.EnrollmentsResponse;
import hu.bme.szoftarch.learning.application.EnrollmentService;
import hu.bme.szoftarch.learning.infrastructure.security.CurrentUser;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Beiratkozás-kezelő végpontok (TERV B – U1). A felhasználó azonosítója mindig
 * a JWT {@code sub} claimjéből jön, soha nem request bodyból.
 */
@RestController
@RequestMapping("/api/v1/learning/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final CurrentUser currentUser;

    public EnrollmentController(EnrollmentService enrollmentService, CurrentUser currentUser) {
        this.enrollmentService = enrollmentService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public EnrollmentsResponse getEnrollments() {
        return enrollmentService.getEnrollments(currentUser.requireUserId());
    }

    @PutMapping("/{languageId}/active")
    public ResponseEntity<Void> activateLanguage(@PathVariable UUID languageId) {
        enrollmentService.activateLanguage(currentUser.requireUserId(), languageId);
        return ResponseEntity.noContent().build();
    }
}
