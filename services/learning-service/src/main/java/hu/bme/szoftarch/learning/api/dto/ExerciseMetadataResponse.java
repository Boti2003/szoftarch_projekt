package hu.bme.szoftarch.learning.api.dto;

import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.Difficulty;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Egy feladat METAADATA a {@code GET /api/v1/lessons/{lessonId}} válaszában
 * (TERV 7.4). Csak prezentációs/listázási mezők; a {@code payload} részletei és
 * a {@code solution} NEM szerepelnek benne.
 */
public record ExerciseMetadataResponse(
        UUID exerciseId,
        ExerciseType type,
        Difficulty difficulty,
        String prompt,
        int orderIndex,
        boolean requiredForCompletion,
        boolean duelEligible,
        int maxPoints) {
}
