package hu.bme.szoftarch.identity.api.dto;

public record FieldErrorDto(
        String field,
        String message
) {
}

