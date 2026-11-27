package hu.bme.szoftarch.social.api.error;

import hu.bme.szoftarch.social.domain.common.AlreadyFriendsException;
import hu.bme.szoftarch.social.domain.common.ConcurrentUpdateException;
import hu.bme.szoftarch.social.domain.common.DomainException;
import hu.bme.szoftarch.social.domain.common.DuelExpiredException;
import hu.bme.szoftarch.social.domain.common.ForbiddenException;
import hu.bme.szoftarch.social.domain.common.IdempotencyKeyRequiredException;
import hu.bme.szoftarch.social.domain.common.InvalidAnswerException;
import hu.bme.szoftarch.social.domain.common.InvalidStateException;
import hu.bme.szoftarch.social.domain.common.RequestExistsException;
import hu.bme.szoftarch.social.domain.common.SelfRequestException;
import hu.bme.szoftarch.social.domain.common.UpstreamUnavailableException;
import hu.bme.szoftarch.social.domain.common.UserNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized {@code @RestControllerAdvice} that renders every domain and
 * framework error as an RFC 9457 {@code application/problem+json} response
 * (requirement 11.1, design "Error Handling").
 *
 * <h2>Problem body fields</h2>
 * Each response is a {@link ProblemDetail} carrying the mandated fields:
 * {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance}
 * (the request path), {@code code} (the stable machine-readable code), and
 * {@code traceId}; validation failures additionally carry a {@code fieldErrors}
 * map (field name &rarr; message).
 *
 * <h2>{@code type} convention</h2>
 * {@code type} is the stable URN {@code urn:social:error:<code>} (e.g.
 * {@code urn:social:error:SELF_REQUEST}). The URN mirrors the {@code code}
 * one-to-one, giving clients a dereferenceable-looking, versionless identifier
 * without committing the service to hosting documentation pages. (The
 * alternative, {@code about:blank}, would discard the per-code distinction RFC
 * 9457 reserves {@code type} for.)
 *
 * <h2>{@code traceId} convention</h2>
 * {@code traceId} is read from the SLF4J {@link MDC} key {@code correlationId}
 * — the same key the infrastructure {@code CorrelationIdAccessor}s use and the
 * one the correlation filter in the security task (task 11) populates. When no
 * id is present (e.g. before that filter is wired), a fresh UUID is minted so
 * every error is still traceable. This keeps the handler working unchanged once
 * full correlation propagation lands.
 *
 * <h2>Status / code mapping</h2>
 * Domain errors are mapped by their stable {@link DomainException#code()} in a
 * single switch (rather than one handler per subtype) so the table stays in one
 * place and matches the design error table exactly:
 * <ul>
 *   <li>{@code SELF_REQUEST} / {@code IDEMPOTENCY_KEY_REQUIRED} &rarr; 400</li>
 *   <li>{@code FORBIDDEN} &rarr; 403</li>
 *   <li>{@code USER_NOT_FOUND} &rarr; 404</li>
 *   <li>{@code ALREADY_FRIENDS} / {@code REQUEST_EXISTS} / {@code INVALID_STATE}
 *       / {@code DUEL_EXPIRED} / {@code CONCURRENT_UPDATE} &rarr; 409</li>
 *   <li>{@code UPSTREAM_UNAVAILABLE} &rarr; 503 (chosen over the equally
 *       acceptable 502: the failure is transient and retryable, which 503
 *       communicates to clients and proxies)</li>
 * </ul>
 *
 * <h2>Framework errors</h2>
 * Bean-validation failures ({@link MethodArgumentNotValidException} on
 * {@code @RequestBody}, {@link ConstraintViolationException} on
 * {@code @RequestParam}/path) &rarr; 400 {@code VALIDATION_FAILED} with
 * {@code fieldErrors}; a malformed/unreadable body
 * ({@link HttpMessageNotReadableException}) &rarr; 400 {@code MALFORMED_REQUEST};
 * anything unmapped &rarr; 500 {@code INTERNAL_ERROR} with a generic detail that
 * never leaks internals.
 *
 * <h2>Boundary with Spring Security (401)</h2>
 * The design maps invalid/expired JWTs to 401 {@code UNAUTHENTICATED}, but that
 * rejection happens in the resource-server authentication entry point before a
 * request reaches any controller, so this advice does not handle it. The
 * security task (task 11) owns the 401 entry point; this handler deliberately
 * stays scoped to application/domain and validation errors to avoid fighting
 * Spring Security's filter chain.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** MDC key holding the current correlation id (populated by task 11's filter). */
    static final String CORRELATION_MDC_KEY = "correlationId";

    /** URN prefix for the problem {@code type}; the stable {@code code} is appended. */
    static final String TYPE_PREFIX = "urn:social:error:";

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /**
     * Maps any {@link DomainException} to its designated status via the stable
     * code, producing the problem body. Unknown codes fall back to 500 so a new
     * domain exception can never silently emit a success response.
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        HttpStatus status = statusForCode(ex.code());
        if (status.is5xxServerError()) {
            log.warn("Domain error {} -> {}", ex.code(), status.value(), ex);
        }
        return problem(status, titleFor(ex.code()), ex.getMessage(), ex.code());
    }

    /**
     * Maps {@code @Valid} request-body failures to 400 {@code VALIDATION_FAILED}
     * with a {@code fieldErrors} map of field name to the first violation message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), messageOrDefault(fieldError.getDefaultMessage()));
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid.", VALIDATION_FAILED);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /**
     * Maps {@code @RequestParam}/path constraint violations to 400
     * {@code VALIDATION_FAILED} with a {@code fieldErrors} map keyed by the
     * leaf parameter name.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (ex.getConstraintViolations() != null) {
            for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
                fieldErrors.putIfAbsent(leafPath(violation), messageOrDefault(violation.getMessage()));
            }
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request parameters are invalid.", VALIDATION_FAILED);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /**
     * Maps an unreadable/malformed request body (bad JSON, wrong type) to 400
     * {@code MALFORMED_REQUEST} without echoing parser internals.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body could not be read.", MALFORMED_REQUEST);
    }

    /**
     * Generic fallback for any unmapped exception: 500 {@code INTERNAL_ERROR}
     * with a fixed, non-revealing detail. The underlying error is logged (not
     * returned) so internals never leak to the client.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.", INTERNAL_ERROR);
    }

    /**
     * Resolves the HTTP status for a stable domain code per the design error
     * table. {@code UPSTREAM_UNAVAILABLE} resolves to 503 (see class docs).
     */
    private static HttpStatus statusForCode(String code) {
        return switch (code) {
            case SelfRequestException.CODE, IdempotencyKeyRequiredException.CODE,
                    InvalidAnswerException.CODE -> HttpStatus.BAD_REQUEST;
            case ForbiddenException.CODE -> HttpStatus.FORBIDDEN;
            case UserNotFoundException.CODE -> HttpStatus.NOT_FOUND;
            case AlreadyFriendsException.CODE,
                 RequestExistsException.CODE,
                 InvalidStateException.CODE,
                 DuelExpiredException.CODE,
                 ConcurrentUpdateException.CODE -> HttpStatus.CONFLICT;
            case UpstreamUnavailableException.CODE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String titleFor(String code) {
        return switch (code) {
            case SelfRequestException.CODE -> "Cannot friend yourself";
            case InvalidAnswerException.CODE -> "Invalid answer";
            case IdempotencyKeyRequiredException.CODE -> "Idempotency-Key required";
            case ForbiddenException.CODE -> "Forbidden";
            case UserNotFoundException.CODE -> "User not found";
            case AlreadyFriendsException.CODE -> "Already friends";
            case RequestExistsException.CODE -> "Friend request already exists";
            case InvalidStateException.CODE -> "Illegal state transition";
            case DuelExpiredException.CODE -> "Duel action expired";
            case ConcurrentUpdateException.CODE -> "Concurrent update conflict";
            case UpstreamUnavailableException.CODE -> "Upstream service unavailable";
            default -> "Request failed";
        };
    }

    /**
     * Builds a {@link ProblemDetail} with all mandated fields set: status,
     * title, detail, {@code type} (urn:social:error:&lt;code&gt;),
     * {@code instance} (request path), {@code code}, and {@code traceId}.
     */
    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                detail == null || detail.isBlank() ? title : detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setProperty("code", code);
        problem.setProperty("traceId", currentTraceId());
        URI instance = currentRequestPath();
        if (instance != null) {
            problem.setInstance(instance);
        }
        return problem;
    }

    /**
     * Returns the current correlation id from MDC, or a fresh UUID when none is
     * present (consistent with the infrastructure CorrelationIdAccessor pattern).
     */
    private static String currentTraceId() {
        String fromContext = MDC.get(CORRELATION_MDC_KEY);
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        return UUID.randomUUID().toString();
    }

    private static URI currentRequestPath() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String uri = attributes.getRequest().getRequestURI();
            if (uri != null && !uri.isBlank()) {
                return URI.create(uri);
            }
        }
        return null;
    }

    private static String leafPath(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 && lastDot < path.length() - 1 ? path.substring(lastDot + 1) : path;
    }

    private static String messageOrDefault(String message) {
        return message == null || message.isBlank() ? "Invalid value" : message;
    }
}
