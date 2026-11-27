package hu.bme.szoftarch.learning.api;

import java.util.UUID;

import hu.bme.szoftarch.learning.api.dto.CreateDuelSetRequest;
import hu.bme.szoftarch.learning.api.dto.DuelAnswerRequest;
import hu.bme.szoftarch.learning.api.dto.DuelAnswerResponse;
import hu.bme.szoftarch.learning.api.dto.DuelSetResponse;
import hu.bme.szoftarch.learning.application.DuelSetService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Belső párbaj-feladatkészlet végpontok (TERV B – U7, 5.5, 7.5). A Social
 * service hívja service-to-service az {@code /internal/v1} útvonalon; a Gateway
 * SOHA nem route-olja. Ezeket a végpontokat kizárólag az
 * {@code X-Internal-Api-Key} header védi (lásd {@code InternalApiKeyFilter} és
 * {@code SecurityConfig}) — NINCS JWT user-kontextus, a résztvevő
 * {@code userId}-k a kérés payloadjában érkeznek.
 */
@RestController
@RequestMapping("/internal/v1/duel-sets")
public class DuelSetController {

    private final DuelSetService duelSetService;

    public DuelSetController(DuelSetService duelSetService) {
        this.duelSetService = duelSetService;
    }

    /**
     * Létrehozza (vagy idempotensen visszaadja) a párbaj feladatkészletét az
     * adott nyelven. Ugyanarra a {@code duelId}-re ismételt hívás a meglévő
     * készletet adja, nem hoz létre újat.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DuelSetResponse createDuelSet(@Valid @RequestBody CreateDuelSetRequest request) {
        return duelSetService.createDuelSet(request.duelId(), request.languageId(), request.expiresAt());
    }

    /**
     * A készlet feladatprezentációi MEGOLDÁS NÉLKÜL, pozíció sorrendben.
     */
    @GetMapping("/{setId}")
    public DuelSetResponse getDuelSet(@PathVariable UUID setId) {
        return duelSetService.getDuelSet(setId);
    }

    /**
     * Egy résztvevő egy válaszának idempotens pontozása. A {@code userId} és a
     * {@code position} a kérés payloadjából jön (NEM JWT-ből).
     */
    @PostMapping("/{setId}/answers")
    public DuelAnswerResponse submitAnswer(@PathVariable UUID setId,
            @Valid @RequestBody DuelAnswerRequest request) {
        return duelSetService.submitAnswer(setId, request.userId(), request.position(), request.answer());
    }
}
