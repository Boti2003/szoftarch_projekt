package hu.bme.szoftarch.learning.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Egy felhasználói beiratkozás a {@code GET /api/v1/learning/enrollments}
 * válaszában. Az {@code active} jelzi az aktív nyelvet (legfeljebb egy).
 */
public record EnrollmentResponse(
        UUID languageId,
        String languageCode,
        String languageName,
        boolean active,
        Instant startedAt) {
}
