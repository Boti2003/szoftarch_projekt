package hu.bme.szoftarch.learning.api;

import java.util.UUID;

import hu.bme.szoftarch.learning.api.dto.AttemptRequest;
import hu.bme.szoftarch.learning.api.dto.AttemptResponse;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse;
import hu.bme.szoftarch.learning.application.AttemptService;
import hu.bme.szoftarch.learning.application.ExerciseService;
import hu.bme.szoftarch.learning.infrastructure.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feladat-prezentáció (TERV B – U2, 7.4) és -beküldés (U4, 5.3) végpontok. A
 * prezentáció a payload alapján, a helyes megoldás nélkül (TERV 5.2); a
 * beküldés a választ kiértékeli és csak az eredményt adja vissza.
 */
@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final AttemptService attemptService;
    private final CurrentUser currentUser;

    public ExerciseController(ExerciseService exerciseService, AttemptService attemptService,
            CurrentUser currentUser) {
        this.exerciseService = exerciseService;
        this.attemptService = attemptService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{exerciseId}")
    public ExercisePresentationResponse getExercise(@PathVariable UUID exerciseId) {
        return exerciseService.getExercisePresentation(exerciseId);
    }

    /**
     * Beküld egy választ egy feladatra (TERV 7.4). A {@code userId} a JWT
     * {@code sub} claimjéből jön (soha nem a bodyból); az
     * {@code Idempotency-Key} kötelező és UUID formátumú.
     */
    @PostMapping("/{exerciseId}/attempts")
    public AttemptResponse submitAttempt(@PathVariable UUID exerciseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AttemptRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        UUID userId = currentUser.requireUserId();
        return attemptService.submit(userId, exerciseId, key, request.answer());
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("idempotency_key_required", "Idempotency-Key header is required");
        }
        try {
            return UUID.fromString(idempotencyKey.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("idempotency_key_invalid", "Idempotency-Key must be a UUID");
        }
    }
}
