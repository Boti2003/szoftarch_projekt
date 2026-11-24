package hu.bme.szoftarch.learning.api.dto;

import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.ProgressStatus;

/**
 * Egy lecke a {@code GET /api/v1/languages/{languageId}/lessons} válaszában,
 * a bejelentkezett felhasználó saját státuszával és elérhetőségével.
 *
 * <p>A {@code status} a {@code lesson_progress} alapján
 * ({@code NOT_STARTED/IN_PROGRESS/COMPLETED}); az {@code unlocked} a feloldási
 * szabály eredménye: az első lecke mindig elérhető, a többi akkor, ha az előző
 * lecke {@code COMPLETED}.</p>
 */
public record LessonListItemResponse(
        UUID id,
        int orderIndex,
        String title,
        ProgressStatus status,
        boolean unlocked) {
}
