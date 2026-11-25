package hu.bme.szoftarch.learning.api;

import java.util.UUID;

import hu.bme.szoftarch.learning.api.dto.ScenarioCompleteResponse;
import hu.bme.szoftarch.learning.api.dto.ScenarioMessageRequest;
import hu.bme.szoftarch.learning.api.dto.ScenarioMessageResponse;
import hu.bme.szoftarch.learning.api.dto.StartScenarioSessionResponse;
import hu.bme.szoftarch.learning.application.AiScenarioService;
import hu.bme.szoftarch.learning.infrastructure.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Párbeszédes ({@code AI_SCENARIO}) feladat végpontjai (TERV B – U8, 5.2). Az
 * összes üzenet a publikus learning API-n megy át (a böngésző nem hamisíthat
 * AI-eredményt/pontot); a {@code userId} a JWT {@code sub} claimjéből jön, soha
 * nem a bodyból, és az {@code Idempotency-Key} fejléc UUID formátumú.
 */
@RestController
@RequestMapping("/api/v1/ai-scenarios")
public class AiScenarioController {

    private final AiScenarioService aiScenarioService;
    private final CurrentUser currentUser;

    public AiScenarioController(AiScenarioService aiScenarioService, CurrentUser currentUser) {
        this.aiScenarioService = aiScenarioService;
        this.currentUser = currentUser;
    }

    /**
     * Elindít egy párbeszédes munkamenetet a megbízható feladat-konfigból
     * (201). Az {@code Idempotency-Key} kötelező; a kérés bodyja nem szükséges.
     */
    @PostMapping("/{exerciseId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public StartScenarioSessionResponse startSession(@PathVariable UUID exerciseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        UUID userId = currentUser.requireUserId();
        return aiScenarioService.startSession(userId, exerciseId, key);
    }

    /**
     * Elküld egy felhasználói üzenetet a futó munkamenetbe ADATKÉNT, és
     * visszaadja az AI válaszát és a körszámot (200).
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public ScenarioMessageResponse sendMessage(@PathVariable UUID sessionId,
            @Valid @RequestBody ScenarioMessageRequest request) {
        UUID userId = currentUser.requireUserId();
        return aiScenarioService.sendMessage(userId, sessionId, request.message());
    }

    /**
     * Lezárja a munkamenetet, menti az eredményt és első sikeres teljesítéskor
     * élesíti a pontszándékot (200). Az {@code Idempotency-Key} kötelező.
     */
    @PostMapping("/sessions/{sessionId}/complete")
    public ScenarioCompleteResponse complete(@PathVariable UUID sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        UUID userId = currentUser.requireUserId();
        return aiScenarioService.complete(userId, sessionId, key);
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
