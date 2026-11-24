package hu.bme.szoftarch.learning.api.dto;

import java.util.UUID;

/**
 * Egy elérhető nyelv a {@code GET /api/v1/languages} válaszában.
 */
public record LanguageResponse(UUID id, String code, String name) {
}
