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
                .collect(java.util.stream.Collectors.joining("; "));

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

        ProblemDetails problem = new ProblemDetails(
                "urn:problem:conflict",
                "Conflict",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "CONFLICT"
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetails> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "urn:problem:unauthorized",
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "UNAUTHORIZED"
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetails> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "urn:problem:not-found",
                "Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "NOT_FOUND"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetails> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "urn:problem:bad-request",
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "BAD_REQUEST"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server error processing request to {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ProblemDetails problem = new ProblemDetails(
                "urn:problem:internal-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected internal error occurred",
                request.getRequestURI(),
                "INTERNAL_ERROR"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(PROBLEM_JSON)
                .body(problem);
    }
}
