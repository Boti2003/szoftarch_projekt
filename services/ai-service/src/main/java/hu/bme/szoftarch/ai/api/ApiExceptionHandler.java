package hu.bme.szoftarch.ai.api;

import hu.bme.szoftarch.ai.domain.LanguageModelException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.NoSuchElementException;

/**
 * Egységes hibafordítás az AI service belső végpontjain. A domain- és
 * alkalmazásszintű kivételeket a megfelelő HTTP-státuszra és RFC 9457
 * {@code application/problem+json} válaszra képezi.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Nincs ilyen erőforrás -> 404. */
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
    }

    /** Érvénytelen állapotátmenet (pl. lezárt session) -> 409. */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "invalid-state", ex.getMessage());
    }

    /** Hibás bemenet (domain validáció) -> 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", ex.getMessage());
    }

    /** Külső nyelvi modell hiba (timeout, sémán kívüli kimenet) -> 502. */
    @ExceptionHandler(LanguageModelException.class)
    public ProblemDetail handleLanguageModel(LanguageModelException ex) {
        return problem(HttpStatus.BAD_GATEWAY, "language-model-error", ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", code);
        return problem;
    }
}
