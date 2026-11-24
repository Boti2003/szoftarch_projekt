package hu.bme.szoftarch.learning.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * A {@code GET /api/v1/lessons/{lessonId}} válasza (TERV 7.4): a lecke elmélete
 * és a feladatainak metaadata {@code orderIndex} sorrendben.
 *
 * <p>A feladatok {@code solution}-je SOHA nem szerepel itt (TERV 5.2).</p>
 */
public record LessonDetailResponse(
        UUID lessonId,
        int orderIndex,
        String title,
        String theoryMarkdown,
        List<ExerciseMetadataResponse> exercises) {
}
