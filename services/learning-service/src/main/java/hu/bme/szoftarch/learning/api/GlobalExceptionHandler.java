package hu.bme.szoftarch.learning.api;

import java.net.URI;
import java.util.List;

import hu.bme.szoftarch.learning.domain.exercise.ExerciseContentException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Globális hibakezelő RFC 9457 {@code application/problem+json} válaszokkal
 * (TERV 7.). Minden válasz tartalmazza: {@code type, title, status, detail,
 * instance, code, traceId}; validációs hibánál {@code fieldErrors} is.
 *
 * <p>Titok, jelszó, token vagy helyes megoldás soha nem kerül a válaszba vagy a
 * logba — csak a kivétel üzenete és a status.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://api.nyelvtanulo.szoftarch/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), ex.getCode(), request, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getCode(), request, null);
    }

    @ExceptionHandler(ExerciseContentException.class)
    public ResponseEntity<ProblemDetail> handleExerciseContent(ExerciseContentException ex,
            HttpServletRequest request) {
        // Szerveroldali adatintegritási hiba (hibás tárolt payload/solution):
        // a kliens egyértelmű problem+json-t kap, nem nyers stacktrace-t.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Exercise Content Error",
                "The exercise content could not be processed", "exercise_content_invalid",
                request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation Failed", "Request validation failed",
                "validation_error", request, fieldErrors);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        HttpServletRequest servletRequest = ((org.springframework.web.context.request.ServletWebRequest) request)
                .getRequest();
        ResponseEntity<ProblemDetail> response = build(HttpStatus.BAD_REQUEST, "Validation Failed",
                "Request validation failed", "validation_error", servletRequest, fieldErrors);
        return new ResponseEntity<>(response.getBody(), response.getHeaders(), response.getStatusCode());
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String title, String detail,
            String code, HttpServletRequest request, @Nullable List<FieldErrorDetail> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + code));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("traceId", resolveTraceId(request));
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        log.warn("Request failed: status={} code={} path={}", status.value(), code, request.getRequestURI());
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, responseHeaders, status);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        return (correlationId == null || correlationId.isBlank())
                ? java.util.UUID.randomUUID().toString()
                : correlationId;
    }

    private static FieldErrorDetail toFieldError(FieldError error) {
        return new FieldErrorDetail(error.getField(), error.getDefaultMessage());
    }

    private static FieldErrorDetail toFieldError(ConstraintViolation<?> violation) {
        return new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage());
    }

    /**
     * Egy mező szintű validációs hiba a {@code fieldErrors} tömbben.
     */
    public record FieldErrorDetail(String field, String message) {
    }
}
