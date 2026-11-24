package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.FieldErrorDto;
import hu.bme.szoftarch.identity.api.dto.ProblemDetails;
import hu.bme.szoftarch.identity.application.exception.ConflictException;
import hu.bme.szoftarch.identity.application.exception.NotFoundException;
import hu.bme.szoftarch.identity.application.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDto> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDto(fe.getField(), fe.getDefaultMessage()))
                .toList();

        String details = fieldErrors.stream()
                .map(fe -> fe.field() + ": " + fe.message())
                .collect(Collectors.joining("; "));

        log.warn("[REQUEST_VALIDATION_FAILED] Validation failed on {}: {} field errors: [{}]",
                request.getRequestURI(), fieldErrors.size(), details);

        ProblemDetails problem = new ProblemDetails(
                "urn:problem:validation-error",
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed for one or more fields: " + details,
                request.getRequestURI(),
                "VALIDATION_ERROR",
                null,
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetails> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.warn("[AUTH_FAILURE: CONFLICT] Registration conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, "conflict", "Conflict", ex.getMessage(), request.getRequestURI(), "CONFLICT");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetails> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", ex.getMessage(), request.getRequestURI(), "UNAUTHORIZED");
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetails> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not Found", ex.getMessage(), request.getRequestURI(), "NOT_FOUND");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetails> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", ex.getMessage(), request.getRequestURI(), "BAD_REQUEST");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server error processing request to {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected internal error occurred", request.getRequestURI(), "INTERNAL_ERROR");
    }

    private ResponseEntity<ProblemDetails> problem(HttpStatus status, String typeSuffix, String title,
                                                   String detail, String uri, String code) {
        ProblemDetails problem = new ProblemDetails(
                "urn:problem:" + typeSuffix,
                title,
                status.value(),
                detail,
                uri,
                code
        );
        return ResponseEntity.status(status)
                .contentType(PROBLEM_JSON)
                .body(problem);
    }
}
