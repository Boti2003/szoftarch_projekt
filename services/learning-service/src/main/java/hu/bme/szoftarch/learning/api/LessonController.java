package hu.bme.szoftarch.learning.api;

import java.util.UUID;

import hu.bme.szoftarch.learning.api.dto.LessonDetailResponse;
import hu.bme.szoftarch.learning.application.ExerciseService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lecke-részlet végpont (TERV B – U2, 7.4): elmélet + a lecke feladatainak
 * metaadata, solution nélkül.
 */
@RestController
@RequestMapping("/api/v1/lessons")
public class LessonController {

    private final ExerciseService exerciseService;

    public LessonController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping("/{lessonId}")
    public LessonDetailResponse getLesson(@PathVariable UUID lessonId) {
        return exerciseService.getLessonDetail(lessonId);
    }
}
