package hu.bme.szoftarch.social.api.duel;

import hu.bme.szoftarch.social.api.CurrentUser;
import hu.bme.szoftarch.social.api.duel.dto.CreateDuelRequest;
import hu.bme.szoftarch.social.api.duel.dto.SubmitAnswerRequest;
import hu.bme.szoftarch.social.api.error.Idempotency;
import hu.bme.szoftarch.social.api.error.PageParams;
import hu.bme.szoftarch.social.api.error.PageResponse;
import hu.bme.szoftarch.social.application.common.PageQuery;
import hu.bme.szoftarch.social.application.common.PageResult;
import hu.bme.szoftarch.social.application.duel.AnswerResultView;
import hu.bme.szoftarch.social.application.duel.DuelAnswerOrchestrator;
import hu.bme.szoftarch.social.application.duel.DuelDetailView;
import hu.bme.szoftarch.social.application.duel.DuelService;
import hu.bme.szoftarch.social.application.duel.DuelSummaryView;
import hu.bme.szoftarch.social.application.duel.DuelView;
import hu.bme.szoftarch.social.application.duel.ExerciseView;
import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.port.AnswerSubmission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the duel lifecycle (requirements 3.1, 3.6, 3.8–3.12),
 * exposing the endpoints under {@code /api/v1/duels} defined in
 * {@code contracts/openapi/social-api.yaml}.
 *
 * <h2>Actor identity (requirement 4.1)</h2>
 * Every handler derives the acting user id from the authenticated JWT's
 * {@code sub} claim via {@link CurrentUser#require(Jwt)} and never from a
 * request body or path, so a caller can only act as themselves. The derived id
 * is always the first argument to the use case.
 *
 * <h2>Idempotency (requirement 9.1)</h2>
 * The retryable mutations — create, accept, reject, and submit-answer — require
 * the {@code Idempotency-Key} header. The header is declared
 * {@code required = false} and validated by {@link Idempotency}, so a missing
 * key is rejected as a 400 {@code IDEMPOTENCY_KEY_REQUIRED} on the stable
 * domain-code path rather than as a framework {@code MissingRequestHeader}
 * error. For submit-answer the key must additionally be a UUID (the orchestrator
 * forwards a {@code UUID} to Learning for at-most-once scoring); a malformed key
 * is rejected the same way via {@link Idempotency#requireKeyAsUuid(String)}.
 *
 * <h2>Serialization</h2>
 * Handlers return the application-layer view records directly; they are plain
 * records whose fields serialize to the response bodies the contract describes.
 * List responses are wrapped in the shared {@link PageResponse} envelope. All
 * error mapping (403/404/409/502-503/400) is centralized in
 * {@code GlobalExceptionHandler}; this controller performs no error handling.
 */
@RestController
@RequestMapping("/api/v1/duels")
public class DuelController {

    private final DuelService duelService;
    private final DuelAnswerOrchestrator duelAnswerOrchestrator;

    public DuelController(DuelService duelService, DuelAnswerOrchestrator duelAnswerOrchestrator) {
        this.duelService = Objects.requireNonNull(duelService);
        this.duelAnswerOrchestrator = Objects.requireNonNull(duelAnswerOrchestrator);
    }

    /**
     * Creates a PENDING duel against an accepted-friend opponent (retryable
     * mutation; requires {@code Idempotency-Key}). Responds 201.
     *
     * @param jwt            the authenticated principal (challenger = {@code sub})
     * @param idempotencyKey the required idempotency key
     * @param request        the opponent and language to duel
     * @return the created duel
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DuelView create(@AuthenticationPrincipal Jwt jwt,
                           @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey,
                           @Valid @RequestBody CreateDuelRequest request) {
        Idempotency.requireKey(idempotencyKey);
        UUID challengerId = CurrentUser.require(jwt);
        return duelService.create(challengerId, request.opponentId(), request.languageId());
    }

    /**
     * Lists the authenticated user's duels, optionally filtered by status, as a
     * paged envelope (requirement 3.12).
     *
     * @param jwt    the authenticated principal
     * @param status optional duel-status filter; {@code null} when absent/blank
     * @param page   zero-based page index (optional)
     * @param size   page size (optional; defaulted and capped)
     * @return a page of duel summaries
     */
    @GetMapping
    public PageResponse<DuelSummaryView> list(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        UUID userId = CurrentUser.require(jwt);
        DuelStatus statusFilter = parseStatus(status);
        PageQuery query = PageParams.toQuery(page, size);
        PageResult<DuelSummaryView> result = duelService.listMyDuels(userId, statusFilter, query);
        return PageResponse.from(result);
    }

    /**
     * Returns a duel's detail for a participant, with the opponent's result
     * gated server-side until the viewer has completed (requirement 3.6).
     *
     * @param jwt the authenticated principal (must be a participant)
     * @param id  the duel id
     * @return the duel detail
     */
    @GetMapping("/{id}")
    public DuelDetailView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = CurrentUser.require(jwt);
        return duelService.get(userId, id);
    }

    /**
     * Accepts a PENDING challenge; only the opponent may accept (retryable
     * mutation; requires {@code Idempotency-Key}) (requirements 3.8, 3.10).
     *
     * @param jwt            the authenticated principal (must be the opponent)
     * @param id             the duel id
     * @param idempotencyKey the required idempotency key
     */
    @PostMapping("/{id}/accept")
    public void accept(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID id,
                       @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey) {
        Idempotency.requireKey(idempotencyKey);
        UUID userId = CurrentUser.require(jwt);
        duelService.accept(userId, id);
    }

    /**
     * Rejects a PENDING challenge; only the opponent may reject (retryable
     * mutation; requires {@code Idempotency-Key}) (requirements 3.9, 3.10).
     *
     * @param jwt            the authenticated principal (must be the opponent)
     * @param id             the duel id
     * @param idempotencyKey the required idempotency key
     */
    @PostMapping("/{id}/reject")
    public void reject(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID id,
                       @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey) {
        Idempotency.requireKey(idempotencyKey);
        UUID userId = CurrentUser.require(jwt);
        duelService.reject(userId, id);
    }

    /**
     * Returns the shared exercise set and starts the requesting participant
     * (ACCEPTED &rarr; IN_PROGRESS); both participants receive the same
     * exercises in the same order (requirement 3.11).
     *
     * @param jwt the authenticated principal (must be a participant)
     * @param id  the duel id
     * @return the duel exercises in presentation order
     */
    @GetMapping("/{id}/exercises")
    public List<ExerciseView> exercises(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = CurrentUser.require(jwt);
        return duelService.exercises(userId, id);
    }

    /**
     * Submits a single answer for scoring (retryable mutation; requires a
     * {@code Idempotency-Key} that must be a UUID). The key is parsed to a
     * {@link UUID} and forwarded to the orchestrator, which passes it to
     * Learning for at-most-once scoring on retry (requirement 5.2).
     *
     * @param jwt            the authenticated principal (must be a participant)
     * @param id             the duel id
     * @param idempotencyKey the required idempotency key (must be a UUID)
     * @param request        the exercise position and the type-specific answer
     * @return the answer outcome and the acting participant's own progress
     */
    @PostMapping("/{id}/answers")
    public AnswerResultView submitAnswer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody SubmitAnswerRequest request) {
        UUID key = Idempotency.requireKeyAsUuid(idempotencyKey);
        UUID userId = CurrentUser.require(jwt);
        AnswerSubmission submission = new AnswerSubmission(request.position(), request.answer());
        return duelAnswerOrchestrator.submitAnswer(userId, id, submission, key);
    }

    /**
     * Maps the optional {@code status} query parameter to a {@link DuelStatus},
     * yielding {@code null} (no filter) when the parameter is absent or blank. A
     * non-blank but unrecognized value surfaces as an
     * {@link IllegalArgumentException} from {@link DuelStatus#valueOf(String)},
     * which the global handler renders as a 400.
     */
    private static DuelStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return DuelStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
