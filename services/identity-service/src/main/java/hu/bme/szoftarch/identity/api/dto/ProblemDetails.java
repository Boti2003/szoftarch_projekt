package hu.bme.szoftarch.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * RFC 9457 compliant error details payload (application/problem+json).
 *
 * NON_NULL ensures that fields inapplicable to a given error type (e.g. 'fieldErrors' on a 409
 * Conflict or 'traceId' when null) are completely omitted from the resulting JSON payload.
 * This keeps error responses concise, clean, and strictly aligned with RFC 9457 guidelines.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String traceId,
        List<FieldErrorDto> fieldErrors
) {
    public ProblemDetails(String type, String title, int status, String detail, String instance, String code) {
        this(type, title, status, detail, instance, code, null, null);
    }
}

