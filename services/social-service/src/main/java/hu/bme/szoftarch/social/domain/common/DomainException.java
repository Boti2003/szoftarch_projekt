package hu.bme.szoftarch.social.domain.common;

import java.util.Objects;

/**
 * Base type for domain-level errors carrying a stable, machine-readable
 * {@code code}.
 *
 * <p>The {@code code} is the contract between the domain and the API layer: a
 * {@code @RestControllerAdvice} maps each code to an RFC 9457
 * {@code application/problem+json} response with the corresponding HTTP status
 * (see design "Error Handling"). Codes are stable strings
 * (e.g. {@code INVALID_STATE}, {@code DUEL_EXPIRED}, {@code FORBIDDEN}) and
 * must not change once published.
 *
 * <p>This type is Spring-free and JPA-free; it lives in the domain layer. The
 * full set of subtypes and codes is defined by task 2.7; this task introduces
 * the base and only the subtypes the duel state machine raises. The
 * {@link #code()} accessor and single-{@code String} constructor form the
 * contract later subtypes extend.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    /**
     * Creates a domain exception with a stable error code and a human-readable
     * detail message.
     *
     * @param code    the stable, machine-readable error code; must not be
     *                {@code null}
     * @param message a human-readable detail (never expose secrets here)
     * @throws NullPointerException if {@code code} is {@code null}
     */
    protected DomainException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    /**
     * Returns the stable, machine-readable error code used by the API layer to
     * select the HTTP status and {@code code} field of the problem response.
     *
     * @return the error code; never {@code null}
     */
    public String code() {
        return code;
    }
}
