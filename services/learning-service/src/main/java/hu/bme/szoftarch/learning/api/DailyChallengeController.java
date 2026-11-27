package hu.bme.szoftarch.learning.api;

import java.util.UUID;

import hu.bme.szoftarch.learning.api.dto.AttemptRequest;
import hu.bme.szoftarch.learning.api.dto.AttemptResponse;
import hu.bme.szoftarch.learning.api.dto.DailyChallengeTodayResponse;
import hu.bme.szoftarch.learning.application.DailyChallengeService;
import hu.bme.szoftarch.learning.infrastructure.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Napi kihívás végpontok (TERV B – U6, 5.4, 7.4). A mai feladatsor lekérése a
 * saját státusszal (solution nélkül), és egy napi feladat beküldése. A
 * {@code userId} a JWT {@code sub} claimjéből jön (soha nem a bodyból); a
 * hitelesítést a {@code SecurityConfig} {@code anyRequest().authenticated()}
 * adja, így token nélkül 401. A beküldésnél az {@code Idempotency-Key} kötelező
 * és UUID formátumú (mint U4).
 */
@RestController
@RequestMapping("/api/v1/daily-challenges")
public class DailyChallengeController {

    private final DailyChallengeService dailyChallengeService;
    private final CurrentUser currentUser;

    public DailyChallengeController(DailyChallengeService dailyChallengeService, CurrentUser currentUser) {
        this.dailyChallengeService = dailyChallengeService;
        this.currentUser = currentUser;
    }

    @GetMapping("/today")
    public DailyChallengeTodayResponse getToday(@RequestParam("languageId") UUID languageId) {
        UUID userId = currentUser.requireUserId();
        return dailyChallengeService.getToday(userId, languageId);
    }

    /**
     * Beküld egy választ a mai napi kihívás egy feladatára (TERV 7.4). A nyelvet
     * (a mai kihívás feloldásához) query paraméterként adja; az
     * {@code Idempotency-Key} kötelező és UUID formátumú.
     */
    @PostMapping("/today/exercises/{exerciseId}/attempts")
    public AttemptResponse submitAttempt(@PathVariable UUID exerciseId,
            @RequestParam("languageId") UUID languageId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AttemptRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        UUID userId = currentUser.requireUserId();
        return dailyChallengeService.submit(userId, languageId, exerciseId, key, request.answer());
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
