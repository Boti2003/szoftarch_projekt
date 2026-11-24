package hu.bme.szoftarch.learning.api.dto;

import java.util.List;
import java.util.UUID;

import org.springframework.lang.Nullable;

/**
 * A bejelentkezett felhasználó beiratkozásai és az aktív nyelv azonosítója
 * ({@code null}, ha nincs aktív nyelv).
 */
public record EnrollmentsResponse(
        List<EnrollmentResponse> enrollments,
        @Nullable UUID activeLanguageId) {
}
